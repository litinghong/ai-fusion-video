package com.stonewu.fusion.service.wallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.common.PageResult;
import com.stonewu.fusion.controller.wallet.vo.AlipayAmountRespVO;
import com.stonewu.fusion.controller.wallet.vo.AlipayPayRespVO;
import com.stonewu.fusion.controller.wallet.vo.TopupBillPageReqVO;
import com.stonewu.fusion.controller.wallet.vo.TopupBillRespVO;
import com.stonewu.fusion.controller.wallet.vo.TopupRespVO;
import com.stonewu.fusion.controller.wallet.vo.WalletCapabilitiesRespVO;
import com.stonewu.fusion.controller.wallet.vo.WalletStatsRespVO;
import com.stonewu.fusion.entity.system.UserThirdPartyBinding;
import com.stonewu.fusion.service.system.NewApiAuthService;
import com.stonewu.fusion.service.system.NewApiHttpClient;
import com.stonewu.fusion.service.system.SystemConfigService;
import com.stonewu.fusion.service.system.UserThirdPartyBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewApiWalletService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final double DEFAULT_QUOTA_PER_UNIT = 500_000D;
    private static final String DEFAULT_QUOTA_DISPLAY_TYPE = "USD";

    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;
    private final NewApiAuthService newApiAuthService;
    private final NewApiHttpClient newApiHttpClient;
    private final UserThirdPartyBindingService userThirdPartyBindingService;

    public WalletCapabilitiesRespVO getCapabilities() {
        return WalletCapabilitiesRespVO.builder()
                .topupEnabled(systemConfigService.isThirdPartyNewApiEnabled()
                        && systemConfigService.isThirdPartyNewApiTopupEnabled())
                .build();
    }

    public WalletStatsRespVO getStats(Long userId) {
        UserThirdPartyBinding binding = requireNewApiBinding(userId);
        Request request = buildUserRequest("/api/user/self", binding)
                .get()
                .build();
        JsonNode dataNode = extractDataOrRoot(executeForData(request, "NewAPI 获取用户信息"));
        JsonNode userNode = dataNode.path("user");
        if (userNode == null || userNode.isMissingNode() || userNode.isNull()) {
            userNode = dataNode;
        }

        NewApiQuotaDisplayConfig displayConfig = fetchQuotaDisplayConfig();
        String affCode = firstText(userNode.path("aff_code"), userNode.path("affCode"));
        return WalletStatsRespVO.builder()
                .quota(asLongOrNull(userNode.path("quota")))
                .usedQuota(asLongOrNull(userNode.path("used_quota")))
                .requestCount(asLongOrNull(userNode.path("request_count")))
                .affQuota(asLongOrNull(userNode.path("aff_quota")))
                .affHistoryQuota(asLongOrNull(userNode.path("aff_history_quota")))
                .affCount(asLongOrNull(userNode.path("aff_count")))
                .quotaPerUnit(displayConfig.quotaPerUnit())
                .quotaDisplayType(displayConfig.quotaDisplayType())
                .affCode(affCode)
                .inviteUrl(buildInviteUrl(affCode))
                .build();
    }

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

    public PageResult<TopupBillRespVO> getTopupBills(Long userId, TopupBillPageReqVO reqVO) {
        UserThirdPartyBinding binding = requireNewApiBinding(userId);
        int pageNo = reqVO.getPageNo() != null ? reqVO.getPageNo() : 1;
        int pageSize = reqVO.getPageSize() != null ? reqVO.getPageSize() : 10;

        HttpUrl.Builder urlBuilder = HttpUrl.parse(buildBaseUrl("/api/user/topup/self"))
                .newBuilder()
                .addQueryParameter("p", String.valueOf(pageNo))
                .addQueryParameter("page_size", String.valueOf(pageSize));
        if (StringUtils.hasText(reqVO.getKeyword())) {
            urlBuilder.addQueryParameter("keyword", reqVO.getKeyword().trim());
        }

        Request request = buildUserRequest(urlBuilder.build().toString(), binding)
                .get()
                .build();
        JsonNode dataNode = executeForData(request, "NewAPI 获取充值账单");

        List<TopupBillRespVO> bills = new ArrayList<>();
        JsonNode items = dataNode.path("items");
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                bills.add(TopupBillRespVO.builder()
                        .id(asLongOrNull(item.path("id")))
                        .userId(asLongOrNull(item.path("user_id")))
                        .amount(asLongOrNull(item.path("amount")))
                        .money(asDoubleOrNull(item.path("money")))
                        .tradeNo(firstText(item.path("trade_no")))
                        .paymentMethod(firstText(item.path("payment_method")))
                        .createTime(asLongOrNull(item.path("create_time")))
                        .completeTime(asLongOrNull(item.path("complete_time")))
                        .status(firstText(item.path("status")))
                        .build());
            }
        }

        Long total = asLongOrNull(dataNode.path("total"));
        return new PageResult<>(bills, total != null ? total : 0L);
    }

    public TopupRespVO topup(Long userId, String key) {
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(400, "兑换码不能为空");
        }
        if (!systemConfigService.isThirdPartyNewApiTopupEnabled()) {
            throw new BusinessException(400, "兑换码充值未开启");
        }

        try {
            UserThirdPartyBinding binding = requireNewApiBinding(userId);

            var bodyNode = objectMapper.createObjectNode();
            bodyNode.put("key", key.trim());

            Request request = buildUserRequest("/api/user/topup", binding)
                    .post(RequestBody.create(objectMapper.writeValueAsString(bodyNode), JSON_MEDIA_TYPE))
                    .build();

            JsonNode root = executeRemoteJson(request, "NewAPI 兑换码充值");
            String message = firstText(
                    root.path("message"),
                    root.path("msg"),
                    root.path("data"),
                    root.path("data").path("message"),
                    root.path("data").path("msg")
            );
            return TopupRespVO.builder()
                    .message(StringUtils.hasText(message) ? message : "兑换码充值成功")
                    .build();
        } catch (IOException e) {
            throw new BusinessException(500, "兑换码充值请求构造失败");
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
        return newApiHttpClient.userJsonRequest(path, binding);
    }

    private String resolveTokenKey(Long userId, UserThirdPartyBinding binding) {
        if (StringUtils.hasText(binding.getNewapiTokenKey())) {
            return binding.getNewapiTokenKey().trim();
        }

        NewApiAuthService.NewApiToken token = findDefaultToken(
                newApiAuthService.listTokens(binding.getThirdPartyUserId(), binding.getSessionValue()));
        if (token == null) {
            newApiAuthService.createDefaultToken(binding.getThirdPartyUserId(), binding.getSessionValue());
            token = findDefaultToken(newApiAuthService.listTokens(binding.getThirdPartyUserId(), binding.getSessionValue()));
        }
        if (token == null || token.getId() == null) {
            throw new BusinessException(500, "NewAPI default 令牌不存在，无法兑换充值码");
        }

        String tokenKey = newApiAuthService.getTokenPlainKey(
                binding.getThirdPartyUserId(),
                binding.getSessionValue(),
                token.getId());
        userThirdPartyBindingService.bindNewApiDefaultToken(userId, token.getId(), tokenKey);
        return tokenKey.trim();
    }

    private NewApiAuthService.NewApiToken findDefaultToken(List<NewApiAuthService.NewApiToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        return tokens.stream()
                .filter(token -> "default".equals(token.getName()))
                .filter(token -> token.getId() != null)
                .findFirst()
                .orElse(null);
    }

    private JsonNode executeForData(Request request, String operation) {
        JsonNode root = executeRemoteJson(request, operation);
        JsonNode dataNode = extractDataOrRoot(root);
        if (dataNode == null || dataNode.isMissingNode() || dataNode.isNull() || dataNode.isTextual()) {
            throw new BusinessException(500, operation + "返回数据异常");
        }
        return dataNode;
    }

    private NewApiQuotaDisplayConfig fetchQuotaDisplayConfig() {
        try {
            Request request = newApiHttpClient.request("/api/status")
                    .get()
                    .build();
            JsonNode statusNode = extractDataOrRoot(executeRemoteJson(request, "NewAPI 获取状态"));
            Double quotaPerUnit = asPositiveDoubleOrNull(statusNode.path("quota_per_unit"));
            String quotaDisplayType = firstText(statusNode.path("quota_display_type"));
            return new NewApiQuotaDisplayConfig(
                    quotaPerUnit != null ? quotaPerUnit : DEFAULT_QUOTA_PER_UNIT,
                    StringUtils.hasText(quotaDisplayType) ? quotaDisplayType : DEFAULT_QUOTA_DISPLAY_TYPE
            );
        } catch (Exception e) {
            log.warn("NewAPI 获取状态失败，使用默认 quota_per_unit: {}", e.getMessage());
            return new NewApiQuotaDisplayConfig(DEFAULT_QUOTA_PER_UNIT, DEFAULT_QUOTA_DISPLAY_TYPE);
        }
    }

    private JsonNode extractDataOrRoot(JsonNode root) {
        return newApiHttpClient.extractDataNode(root);
    }

    private JsonNode executeRemoteJson(Request request, String operation) {
        return newApiHttpClient.executeJson(request, operation);
    }

    private String buildBaseUrl(String path) {
        return newApiHttpClient.buildUrl(path);
    }

    private String buildInviteUrl(String affCode) {
        if (!StringUtils.hasText(affCode)) {
            return null;
        }
        String baseUrl = systemConfigService.getThirdPartyNewApiBaseUrl();
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String encodedAffCode = URLEncoder.encode(affCode.trim(), StandardCharsets.UTF_8);
        return normalized + "/register?aff=" + encodedAffCode;
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

    private Double asPositiveDoubleOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        double value;
        if (node.isNumber()) {
            value = node.asDouble();
        } else {
            String text = node.asText(null);
            if (!StringUtils.hasText(text)) {
                return null;
            }
            try {
                value = Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return value > 0 ? value : null;
    }

    private Double asDoubleOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        String text = node.asText(null);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record NewApiQuotaDisplayConfig(Double quotaPerUnit, String quotaDisplayType) {
    }
}
