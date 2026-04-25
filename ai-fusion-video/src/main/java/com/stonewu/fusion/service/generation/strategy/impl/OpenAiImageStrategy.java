package com.stonewu.fusion.service.generation.strategy.impl;

import cn.hutool.core.util.StrUtil;
import com.stonewu.fusion.common.BusinessException;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.images.ImageGenerateParams;
import com.openai.models.images.ImageModel;
import com.openai.core.http.HttpResponseFor;
import com.openai.core.http.Headers;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.entity.generation.ImageItem;
import com.stonewu.fusion.entity.generation.ImageTask;
import com.stonewu.fusion.service.ai.AiModelService;
import com.stonewu.fusion.service.generation.ImageGenerationService;
import com.stonewu.fusion.service.generation.strategy.ImageGenerationStrategy;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

/**
 * OpenAI 图片生成策略
 * <p>
 * 支持 DALL·E 3、DALL·E 2、gpt-image-1 等模型文生图。
 * 通过 openai-java SDK 的 client.images().generate() 调用。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiImageStrategy implements ImageGenerationStrategy {

    private final ImageGenerationService imageGenerationService;
    private final AiModelService aiModelService;

    @Override
    public String getName() {
        return "openai";
    }

    @Override
    public List<String> generate(String prompt, String modelCode, int width, int height, int count,
                                  List<String> imageUrls, ApiConfig apiConfig) {
        if (imageUrls != null && !imageUrls.isEmpty()) {
            throw new BusinessException("当前图片模型 " + modelCode + " 使用的是 OpenAI 文生图接口，不支持参考图输入");
        }

        String resolvedBaseUrl = resolveBaseUrl(apiConfig);
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder().apiKey(apiConfig.getApiKey());
        if (StrUtil.isNotBlank(resolvedBaseUrl)) {
            builder.baseUrl(resolvedBaseUrl);
        }
        OpenAIClient client = builder.build();

        ImageGenerateParams.Builder paramsBuilder = ImageGenerateParams.builder()
                .prompt(prompt)
                .model(ImageModel.of(modelCode))
                .n((long) count);

        // 尺寸映射
        ImageGenerateParams.Size size = mapSize(width, height);
        if (size != null) {
            paramsBuilder.size(size);
        }

        ImageGenerateParams params = paramsBuilder.build();
        log.info("[OpenAI] 调用文生图 API: model={}, prompt={}, size={}x{}", modelCode, prompt, width, height);
        logRequestDebug(apiConfig, resolvedBaseUrl, modelCode, prompt, count, size);

        try (HttpResponseFor<?> response = client.images().withRawResponse().generate(params)) {
            int statusCode = response.statusCode();
            Headers headers = response.headers();
            String contentType = headers.values("content-type").stream().findFirst().orElse("unknown");
            String requestId = response.requestId().orElse("-");
            String bodyText = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);

            // 打印 OpenAI 原始响应（截断以防日志过大）
            log.info("[OpenAI] 原始响应: status={}, requestId={}, contentType={}, bodyChars={}, body={}",
                    statusCode, requestId, contentType, bodyText.length(), truncateForLog(bodyText));

            if (statusCode >= 400) {
                throw new RuntimeException("OpenAI 接口返回失败状态: " + statusCode);
            }

            JSONObject json;
            try {
                json = JSONUtil.parseObj(bodyText);
            } catch (Exception ex) {
                throw new RuntimeException("OpenAI 返回内容不是 JSON，详见日志中的原始响应", ex);
            }

            JSONArray data = json.getJSONArray("data");
            if (data == null || data.isEmpty()) {
                throw new RuntimeException("OpenAI 返回 data 为空，详见日志中的原始响应");
            }

            List<String> urls = IntStream.range(0, data.size())
                    .mapToObj(data::getJSONObject)
                    .map(item -> item != null ? item.getStr("url") : null)
                    .filter(StrUtil::isNotBlank)
                    .toList();
            if (urls.isEmpty()) {
                throw new RuntimeException("OpenAI 响应中未找到 data[].url，详见日志中的原始响应");
            }
            return urls;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("调用 OpenAI 文生图失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String submit(ImageTask task, ApiConfig apiConfig) {
        AiModel model = resolveModel(task);
        String modelCode = (model != null && StrUtil.isNotBlank(model.getCode())) ? model.getCode() : "dall-e-3";
        int[] size = resolveDefaultSize(model, task);
        int count = (task.getCount() != null && task.getCount() > 0) ? task.getCount() : 1;

        // 解析参考图（图生图场景）
        List<String> imageUrls = parseRefImageUrls(task.getRefImageUrls());

        // 复用纯 API 调用
        List<String> urls = generate(task.getPrompt(), modelCode, size[0], size[1], count, imageUrls, apiConfig);

        // 更新数据库记录
        List<ImageItem> items = imageGenerationService.listItems(task.getId());
        for (int i = 0; i < urls.size() && i < items.size(); i++) {
            ImageItem item = items.get(i);
            item.setImageUrl(urls.get(i));
            item.setStatus(1);
            imageGenerationService.updateItem(item);
        }

        task.setSuccessCount(Math.min(urls.size(), items.size()));
        imageGenerationService.update(task);

        log.info("[OpenAI] 文生图完成: taskId={}, imageCount={}", task.getTaskId(), urls.size());
        return task.getTaskId();
    }

    @Override
    public void poll(String platformTaskId, ImageTask task, ApiConfig apiConfig) {
        // OpenAI images.generate 是同步 API，submit 中已处理完成，无需轮询
    }

    private AiModel resolveModel(ImageTask task) {
        if (task.getModelId() != null) {
            if (task.getUserId() == null) {
                return null;
            }
            AiModel model = aiModelService.getByIdForUser(task.getModelId(), task.getUserId());
            if (model != null && StrUtil.isNotBlank(model.getCode())) {
                return model;
            }
        }
        return null;
    }

    /**
     * 将像素尺寸映射到 OpenAI 支持的枚举值
     */
    private ImageGenerateParams.Size mapSize(int width, int height) {
        String sizeStr = width + "x" + height;
        return switch (sizeStr) {
            case "256x256" -> ImageGenerateParams.Size._256X256;
            case "512x512" -> ImageGenerateParams.Size._512X512;
            case "1024x1024" -> ImageGenerateParams.Size._1024X1024;
            case "1024x1792" -> ImageGenerateParams.Size._1024X1792;
            case "1792x1024" -> ImageGenerateParams.Size._1792X1024;
            default -> ImageGenerateParams.Size._1024X1024;
        };
    }

    private String truncateForLog(String text) {
        if (text == null) {
            return "";
        }
        int maxLen = 4000;
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...(truncated, totalChars=" + text.length() + ")";
    }

    private void logRequestDebug(ApiConfig apiConfig, String baseUrl, String modelCode, String prompt, int count, ImageGenerateParams.Size size) {
        String endpoint = baseUrl + "/images/generations";
        JSONObject headers = JSONUtil.createObj()
                .set("Authorization", "Bearer " + maskApiKey(apiConfig.getApiKey()))
                .set("Content-Type", "application/json")
                .set("Accept", "application/json");
        JSONObject body = JSONUtil.createObj()
                .set("model", modelCode)
                .set("prompt", prompt)
                .set("n", count)
                .set("size", sizeToText(size));

        log.info("[OpenAI] 请求详情: baseUrl={}, endpoint={}, headers={}, body={}",
                baseUrl,
                endpoint,
                headers.toString(),
                truncateForLog(body.toString()));
    }

    private String resolveBaseUrl(ApiConfig apiConfig) {
        String rawBaseUrl = StrUtil.blankToDefault(apiConfig.getApiUrl(), "https://api.openai.com/v1");
        String normalized = StrUtil.removeSuffix(rawBaseUrl.trim(), "/");
        if (!shouldAutoAppendV1Path(apiConfig)) {
            return normalized;
        }
        if (normalized.matches("(?i).*/v\\d+$")) {
            return normalized;
        }
        return normalized + "/v1";
    }

    private boolean shouldAutoAppendV1Path(ApiConfig apiConfig) {
        String platform = apiConfig != null ? StrUtil.nullToEmpty(apiConfig.getPlatform()) : "";
        if ("openai_compatible".equalsIgnoreCase(platform) || "newapi".equalsIgnoreCase(platform)) {
            return apiConfig.getAutoAppendV1Path() == null || apiConfig.getAutoAppendV1Path();
        }
        return true;
    }

    private String sizeToText(ImageGenerateParams.Size size) {
        if (size == null) {
            return null;
        }
        if (size == ImageGenerateParams.Size._256X256) {
            return "256x256";
        }
        if (size == ImageGenerateParams.Size._512X512) {
            return "512x512";
        }
        if (size == ImageGenerateParams.Size._1024X1024) {
            return "1024x1024";
        }
        if (size == ImageGenerateParams.Size._1024X1792) {
            return "1024x1792";
        }
        if (size == ImageGenerateParams.Size._1792X1024) {
            return "1792x1024";
        }
        return size.toString();
    }

    private String maskApiKey(String apiKey) {
        if (StrUtil.isBlank(apiKey)) {
            return "(empty)";
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "..." + trimmed.substring(trimmed.length() - 4);
    }
}
