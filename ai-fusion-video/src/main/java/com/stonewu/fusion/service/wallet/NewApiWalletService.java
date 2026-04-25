package com.stonewu.fusion.service.wallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.controller.wallet.vo.AlipayAmountRespVO;
import com.stonewu.fusion.controller.wallet.vo.AlipayPayRespVO;
import com.stonewu.fusion.entity.system.UserThirdPartyBinding;
import com.stonewu.fusion.service.system.SystemConfigService;
import com.stonewu.fusion.service.system.UserThirdPartyBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewApiWalletService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;
    private final UserThirdPartyBindingService userThirdPartyBindingService;

    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();

    public AlipayAmountRespVO getAlipayAmounts(Long userId) {
        UserThirdPartyBinding binding = requireNewApiBinding(userId);
        Request request = buildUserRequest("/api/user/alipay/amounts", binding)
                .get()
                .build();
        JsonNode dataNode = executeForData(request, "NewAPI 获取支付宝参考充值金额");

        List<AlipayAmountRespVO.ReferenceAmount> referenceAmounts = new ArrayList<>();
        JsonNode items = dataNode.path("reference_amounts");
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                referenceAmounts.add(AlipayAmountRespVO.ReferenceAmount.builder()
                        .amount(firstText(item.path("amount")))
                        .currency(firstText(item.path("currency")))
                        .productId(firstText(item.path("product_id")))
                        .name(firstText(item.path("name")))
                        .quota(asLongOrNull(item.path("quota")))
                        .build());
            }
        }

        return AlipayAmountRespVO.builder()
                .minAmount(firstText(dataNode.path("min_amount")))
                .currency(firstText(dataNode.path("currency")))
                .referenceAmounts(referenceAmounts)
                .build();
    }

    public AlipayPayRespVO createAlipayPayUrl(Long userId, String amount, String productId) {
        if (!StringUtils.hasText(amount) && !StringUtils.hasText(productId)) {
            throw new BusinessException(400, "充值金额不能为空");
        }

        try {
            UserThirdPartyBinding binding = requireNewApiBinding(userId);
            var bodyNode = objectMapper.createObjectNode();
            bodyNode.put("payment_method", "alipay");
            if (StringUtils.hasText(productId)) {
                bodyNode.put("product_id", productId.trim());
            } else {
                bodyNode.put("amount", amount.trim());
            }

            Request request = buildUserRequest("/api/user/alipay/pay", binding)
                    .post(RequestBody.create(objectMapper.writeValueAsString(bodyNode), JSON_MEDIA_TYPE))
                    .build();
            JsonNode dataNode = executeForData(request, "NewAPI 创建支付宝充值链接");

            String checkoutUrl = firstText(dataNode.path("checkout_url"));
            if (!StringUtils.hasText(checkoutUrl)) {
                throw new BusinessException(500, "NewAPI 未返回支付宝充值链接");
            }

            return AlipayPayRespVO.builder()
                    .checkoutUrl(checkoutUrl)
                    .orderId(firstText(dataNode.path("order_id")))
                    .amount(firstText(dataNode.path("amount")))
                    .creditedAmount(asLongOrNull(dataNode.path("credited_amount")))
                    .build();
        } catch (IOException e) {
            throw new BusinessException(500, "支付宝充值请求构造失败");
        }
    }

    private UserThirdPartyBinding requireNewApiBinding(Long userId) {
        if (!systemConfigService.isThirdPartyNewApiEnabled()) {
            throw new BusinessException(400, "NewAPI 集成未开启");
        }
        UserThirdPartyBinding binding = userThirdPartyBindingService.getNewApiBinding(userId);
        if (binding == null) {
            throw new BusinessException(400, "当前账号未绑定 NewAPI 用户");
        }
        if (binding.getThirdPartyUserId() == null) {
            throw new BusinessException(400, "缺少 NewAPI 用户ID");
        }
        if (!StringUtils.hasText(binding.getSessionValue())) {
            throw new BusinessException(400, "缺少 NewAPI 登录 session，请重新登录");
        }
        return binding;
    }

    private Request.Builder buildUserRequest(String path, UserThirdPartyBinding binding) {
        return new Request.Builder()
                .url(buildBaseUrl(path))
                .header("Content-Type", "application/json")
                .header("New-Api-User", String.valueOf(binding.getThirdPartyUserId()))
                .header("Cookie", buildSessionCookieHeader(binding.getSessionValue()));
    }

    private JsonNode executeForData(Request request, String operation) {
        try (Response response = okHttpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!StringUtils.hasText(body)) {
                throw new BusinessException(500, operation + "返回为空");
            }

            JsonNode root = objectMapper.readTree(body);
            if (!response.isSuccessful()) {
                throw new BusinessException(response.code(), extractErrorMessage(root, operation));
            }
            checkRemoteSuccess(root, operation);

            JsonNode dataNode = root.path("data");
            if (dataNode == null || dataNode.isMissingNode() || dataNode.isNull() || dataNode.isTextual()) {
                throw new BusinessException(500, operation + "返回数据异常");
            }
            return dataNode;
        } catch (IOException e) {
            log.error("{} 请求失败", operation, e);
            throw new BusinessException(500, operation + "失败");
        }
    }

    private void checkRemoteSuccess(JsonNode root, String operation) {
        if (root == null || root.isNull()) {
            throw new BusinessException(500, operation + "返回异常");
        }
        if (root.has("success") && !root.path("success").asBoolean(true)) {
            throw new BusinessException(400, extractErrorMessage(root, operation));
        }
        if (root.has("message") && "error".equalsIgnoreCase(root.path("message").asText(""))) {
            throw new BusinessException(400, extractErrorMessage(root, operation));
        }
        if (root.has("code")) {
            int code = root.path("code").asInt(0);
            if (code != 0 && code != 200) {
                throw new BusinessException(400, extractErrorMessage(root, operation));
            }
        }
    }

    private String extractErrorMessage(JsonNode root, String operation) {
        String message = firstText(root.path("data"), root.path("message"), root.path("msg"));
        return StringUtils.hasText(message) ? message : operation + "失败";
    }

    private String buildBaseUrl(String path) {
        String baseUrl = systemConfigService.getThirdPartyNewApiBaseUrl();
        if (!StringUtils.hasText(baseUrl) || (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://"))) {
            throw new BusinessException(500, "NewAPI 地址配置无效: " + baseUrl);
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalized + path;
    }

    private String buildSessionCookieHeader(String sessionValue) {
        String raw = sessionValue.trim();
        if (raw.regionMatches(true, 0, "session=", 0, "session=".length())) {
            raw = raw.substring("session=".length());
        }
        int semicolonIndex = raw.indexOf(';');
        if (semicolonIndex >= 0) {
            raw = raw.substring(0, semicolonIndex).trim();
        }
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(400, "NewAPI session 无效");
        }
        return "session=" + raw;
    }

    private String firstText(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }
            String value = node.asText(null);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Long asLongOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        String text = node.asText(null);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
