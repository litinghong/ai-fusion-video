package com.stonewu.fusion.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.common.PageResult;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.mapper.ai.ApiConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiConfigService {

    private final ApiConfigMapper apiConfigMapper;

    @Transactional
    public Long createApiConfig(ApiConfig apiConfig, Long userId) {
        if (userId == null) {
            throw new BusinessException(400, "缺少用户上下文");
        }
        if (apiConfig.getAutoAppendV1Path() == null) {
            apiConfig.setAutoAppendV1Path(true);
        }
        apiConfig.setUserId(userId);
        apiConfig.setApiUrl(normalizeApiUrl(apiConfig.getPlatform(), apiConfig.getApiUrl()));
        apiConfigMapper.insert(apiConfig);
        return apiConfig.getId();
    }

    @Transactional
    public void updateApiConfig(Long userId, Long id, String name, String platform, String apiUrl,
                                Boolean autoAppendV1Path,
                                String apiKey, String appId, String appSecret,
                                Long modelId, Integer status, String remark) {
        ApiConfig config = getByIdForUser(id, userId);
        if (config == null) {
            throw new BusinessException(404, "API配置不存在");
        }
        String effectivePlatform = platform != null ? platform : config.getPlatform();
        if (name != null) config.setName(name);
        if (platform != null) config.setPlatform(platform);
        if (apiUrl != null) config.setApiUrl(normalizeApiUrl(effectivePlatform, apiUrl));
        if (autoAppendV1Path != null) config.setAutoAppendV1Path(autoAppendV1Path);
        if (apiKey != null) config.setApiKey(apiKey);
        if (appId != null) config.setAppId(appId);
        if (appSecret != null) config.setAppSecret(appSecret);
        if (modelId != null) config.setModelId(modelId);
        if (status != null) config.setStatus(status);
        if (remark != null) config.setRemark(remark);
        apiConfigMapper.updateById(config);
    }

    @Transactional
    public void deleteApiConfig(Long userId, Long id) {
        ApiConfig config = getByIdForUser(id, userId);
        if (config == null) {
            throw new BusinessException(404, "API配置不存在");
        }
        apiConfigMapper.deleteById(id);
    }

    public ApiConfig getById(Long id) {
        return apiConfigMapper.selectById(id);
    }

    public ApiConfig getByIdForUser(Long id, Long userId) {
        if (id == null || userId == null) {
            return null;
        }
        return apiConfigMapper.selectOne(new LambdaQueryWrapper<ApiConfig>()
                .eq(ApiConfig::getId, id)
                .eq(ApiConfig::getUserId, userId)
                .last("LIMIT 1"));
    }

    public ApiConfig getByNameAndPlatformForUser(Long userId, String name, String platform) {
        if (userId == null || StrUtil.isBlank(name) || StrUtil.isBlank(platform)) {
            return null;
        }
        return apiConfigMapper.selectOne(new LambdaQueryWrapper<ApiConfig>()
                .eq(ApiConfig::getUserId, userId)
                .eq(ApiConfig::getName, name)
                .eq(ApiConfig::getPlatform, platform)
                .last("LIMIT 1"));
    }

    public PageResult<ApiConfig> getPageByUser(Long userId, String name, String platform,
                                               Integer status, int pageNo, int pageSize) {
        LambdaQueryWrapper<ApiConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiConfig::getUserId, userId)
                .like(name != null, ApiConfig::getName, name)
                .eq(platform != null, ApiConfig::getPlatform, platform)
                .eq(status != null, ApiConfig::getStatus, status)
                .orderByDesc(ApiConfig::getId);
        return PageResult.of(apiConfigMapper.selectPage(new Page<>(pageNo, pageSize), wrapper));
    }

    public List<ApiConfig> getEnabledListByUser(Long userId) {
        return apiConfigMapper.selectList(new LambdaQueryWrapper<ApiConfig>()
                .eq(ApiConfig::getUserId, userId)
                .eq(ApiConfig::getStatus, 1));
    }

    /**
     * 按平台标识获取启用的 API 配置列表
     */
    public List<ApiConfig> getListByPlatform(Long userId, String platform) {
        return apiConfigMapper.selectList(new LambdaQueryWrapper<ApiConfig>()
                .eq(ApiConfig::getUserId, userId)
                .eq(ApiConfig::getStatus, 1)
                .eq(ApiConfig::getPlatform, platform));
    }

    /**
     * 按多个平台标识获取启用的 API 配置列表
     */
    public List<ApiConfig> getListByPlatforms(Long userId, List<String> platforms) {
        return apiConfigMapper.selectList(new LambdaQueryWrapper<ApiConfig>()
                .eq(ApiConfig::getUserId, userId)
                .eq(ApiConfig::getStatus, 1)
                .in(ApiConfig::getPlatform, platforms));
    }

    @Transactional
    public ApiConfig upsertNewApiConfig(Long userId, String apiUrl, String apiKey) {
        ApiConfig existing = getByNameAndPlatformForUser(userId, "NewAPI", "newapi");
        String normalizedApiUrl = normalizeApiUrl("newapi", apiUrl);
        if (existing == null) {
            ApiConfig created = ApiConfig.builder()
                    .userId(userId)
                    .name("NewAPI")
                    .platform("newapi")
                    .apiUrl(normalizedApiUrl)
                    .autoAppendV1Path(true)
                    .apiKey(apiKey)
                    .status(1)
                    .remark("自动同步生成")
                    .build();
            apiConfigMapper.insert(created);
            return created;
        }
        existing.setApiUrl(normalizedApiUrl);
        existing.setApiKey(apiKey);
        existing.setStatus(1);
        if (existing.getAutoAppendV1Path() == null) {
            existing.setAutoAppendV1Path(true);
        }
        apiConfigMapper.updateById(existing);
        return existing;
    }

    private String normalizeApiUrl(String platform, String apiUrl) {
        if (StrUtil.isBlank(apiUrl)) {
            return null;
        }
        String normalizedApiUrl = apiUrl.trim();
        String defaultApiUrl = getPlatformDefaultApiUrl(platform);
        if (StrUtil.isNotBlank(defaultApiUrl) && isSameApiUrl(normalizedApiUrl, defaultApiUrl)) {
            return null;
        }
        return normalizedApiUrl;
    }

    private boolean isSameApiUrl(String currentApiUrl, String defaultApiUrl) {
        return normalizeComparableApiUrl(currentApiUrl)
                .equalsIgnoreCase(normalizeComparableApiUrl(defaultApiUrl));
    }

    private String normalizeComparableApiUrl(String apiUrl) {
        if (StrUtil.isBlank(apiUrl)) {
            return "";
        }
        return apiUrl.trim().replaceAll("/+$", "");
    }

    private String getPlatformDefaultApiUrl(String platform) {
        if (StrUtil.isBlank(platform)) {
            return null;
        }
        return switch (platform) {
            case "openai_compatible", "openai" -> "https://api.openai.com";
            case "newapi" -> "http://localhost:3001";
            case "volcengine" -> "https://ark.cn-beijing.volces.com";
            case "vertex_ai" -> "us-central1";
            case "GoogleFlowReverseApi" -> "http://localhost:8000";
            case "dashscope" -> "https://dashscope.aliyuncs.com";
            case "anthropic" -> "https://api.anthropic.com";
            case "ollama" -> "http://localhost:11434";
            default -> null;
        };
    }
}
