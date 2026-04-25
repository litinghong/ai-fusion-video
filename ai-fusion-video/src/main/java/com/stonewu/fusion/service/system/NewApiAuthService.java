package com.stonewu.fusion.service.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonewu.fusion.common.BusinessException;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * NewAPI 第三方账号认证服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewApiAuthService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;
    private final NewApiHttpClient newApiHttpClient;

    public boolean isEnabled() {
        return systemConfigService.isThirdPartyNewApiEnabled();
    }

    public boolean isEmailVerificationEnabled() {
        return systemConfigService.isThirdPartyNewApiEmailVerificationEnabled();
    }

    public boolean isUserModelConfigDisabled() {
        return systemConfigService.isThirdPartyNewApiUserModelConfigDisabled();
    }

    /**
     * 注册场景下是否需要邮箱验证码：
     * 1. 本地显式开启时，直接要求；
     * 2. 本地未开启时，兜底探测 NewAPI 远端状态，避免出现前端未要求验证码但远端强制要求的情况。
     */
    public boolean isRegisterEmailVerificationRequired() {
        if (!isEnabled()) {
            return false;
        }
        if (isEmailVerificationEnabled()) {
            return true;
        }
        return Boolean.TRUE.equals(fetchRemoteEmailVerificationEnabled());
    }

    public String getBaseUrl() {
        return systemConfigService.getThirdPartyNewApiBaseUrl();
    }

    public void register(String username,
                         String password,
                         String nickname,
                         String email,
                         String emailCode,
                         String turnstile) {
        try {
            var bodyNode = objectMapper.createObjectNode();
            bodyNode.put("username", username);
            bodyNode.put("password", password);
            bodyNode.put("password2", password);
            if (StringUtils.hasText(nickname)) {
                bodyNode.put("display_name", nickname);
            }
            if (StringUtils.hasText(email)) {
                bodyNode.put("email", email);
            }
            if (StringUtils.hasText(emailCode)) {
                // 兼容不同接口字段命名
                bodyNode.put("email_code", emailCode);
                bodyNode.put("verification_code", emailCode);
            }

            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(buildBaseUrl("/api/user/register"));
            if (turnstile != null) {
                urlBuilder.queryParam("turnstile", turnstile);
            }

            Request request = newApiHttpClient.jsonRequest(urlBuilder.build(true).toUriString())
                    .post(RequestBody.create(objectMapper.writeValueAsString(bodyNode), JSON_MEDIA_TYPE))
                    .build();

            executeJson(request, "NewAPI 注册");
        } catch (IOException e) {
            throw new BusinessException(500, "NewAPI 注册请求构造失败");
        }
    }

    public NewApiLoginResult login(String username, String password, String turnstile) {
        try {
            var bodyNode = objectMapper.createObjectNode();
            bodyNode.put("username", username);
            bodyNode.put("password", password);

            UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(buildBaseUrl("/api/user/login"));
            if (turnstile != null) {
                urlBuilder.queryParam("turnstile", turnstile);
            }

            Request request = newApiHttpClient.jsonRequest(urlBuilder.build(true).toUriString())
                    .post(RequestBody.create(objectMapper.writeValueAsString(bodyNode), JSON_MEDIA_TYPE))
                    .build();

            NewApiHttpClient.NewApiResponse response = newApiHttpClient.execute(request, "NewAPI 登录");
            try {
                JsonNode root = parseBody(response.body(), "NewAPI 登录");
                if (!response.isSuccessful()) {
                    String message = firstText(root.path("data"), root.path("message"), root.path("msg"));
                    throw new BusinessException(response.code(), StringUtils.hasText(message) ? message : "NewAPI 登录失败");
                }
                checkNewApiSuccess(root, "NewAPI 登录");

                JsonNode dataNode = extractDataNode(root);
                JsonNode userNode = dataNode.path("user");
                if (userNode == null || userNode.isMissingNode() || userNode.isNull()) {
                    userNode = dataNode;
                }

                String token = firstText(dataNode.path("token"), root.path("token"));
                String responseUsername = firstText(userNode.path("username"), dataNode.path("username"));
                if (!StringUtils.hasText(responseUsername)) {
                    responseUsername = username;
                }

                String sessionValue = extractCookieValue(response.headers().values("Set-Cookie"), "session");
                if (!StringUtils.hasText(sessionValue)) {
                    throw new BusinessException(400, "NewAPI 登录成功但未返回 session Cookie");
                }

                String newApiUserHeader = firstNonBlank(
                        response.headers().get("new-api-user"),
                        response.headers().get("New-Api-User")
                );
                Long userId = asLongOrNull(userNode.path("id"));
                if (userId == null) {
                    userId = parseLongOrNull(newApiUserHeader);
                }
                if (!StringUtils.hasText(newApiUserHeader) && userId != null) {
                    newApiUserHeader = String.valueOf(userId);
                }

                return NewApiLoginResult.builder()
                        .userId(userId)
                        .username(responseUsername)
                        .token(token)
                        .sessionValue(sessionValue)
                        .newApiUserHeader(newApiUserHeader)
                        .build();
            } catch (IOException e) {
                throw new BusinessException(500, "NewAPI 登录响应解析失败");
            }
        } catch (IOException e) {
            log.error("NewAPI 登录调用失败", e);
            throw new BusinessException(500, "调用 NewAPI 登录失败");
        }
    }

    public NewApiUserProfile getSelf(String token, String sessionValue, String newApiUserHeader) {
        Request.Builder requestBuilder = newApiHttpClient.userJsonRequest(
                        "/api/user/self",
                        newApiUserHeader,
                        sessionValue)
                .get();

        if (StringUtils.hasText(token)) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        JsonNode root = executeJson(requestBuilder.build(), "NewAPI 获取用户信息");
        JsonNode dataNode = extractDataNode(root);
        JsonNode userNode = dataNode.path("user");
        if (userNode == null || userNode.isMissingNode() || userNode.isNull()) {
            userNode = dataNode;
        }

        return NewApiUserProfile.builder()
                .id(asLongOrNull(userNode.path("id")))
                .username(firstText(userNode.path("username"), dataNode.path("username")))
                .nickname(firstText(userNode.path("display_name"), userNode.path("nickname"), userNode.path("username")))
                .email(firstText(userNode.path("email"), dataNode.path("email")))
                .build();
    }

    public void sendVerificationCode(String email, String turnstile) {
        UriComponentsBuilder urlBuilder = UriComponentsBuilder.fromHttpUrl(buildBaseUrl("/api/verification"))
                .queryParam("email", email)
                .queryParam("purpose", "0");
        if (turnstile != null) {
            urlBuilder.queryParam("turnstile", turnstile);
        }

        Request request = newApiHttpClient.request(urlBuilder.build(true).toUriString())
                .get()
                .build();

        executeJson(request, "NewAPI 发送邮箱验证码");
    }

    public List<NewApiToken> listTokens(Long newApiUserId, String sessionValue) {
        String listUrl = buildBaseUrl("/api/token/");
        Request getRequest = buildManagementRequestBuilder(listUrl, newApiUserId, sessionValue)
                .get()
                .build();
        try {
            JsonNode root = executeJson(getRequest, "NewAPI 获取令牌列表(GET)");
            return parseTokenItems(root);
        } catch (BusinessException ex) {
            log.warn("NewAPI 获取令牌列表(GET)失败，尝试 POST 兼容: {}", ex.getMessage());
        }

        Request postRequest = buildManagementRequestBuilder(listUrl, newApiUserId, sessionValue)
                .post(RequestBody.create("{}", JSON_MEDIA_TYPE))
                .build();
        JsonNode root = executeJson(postRequest, "NewAPI 获取令牌列表(POST)");
        return parseTokenItems(root);
    }

    public void createDefaultToken(Long newApiUserId, String sessionValue) {
        try {
            var bodyNode = objectMapper.createObjectNode();
            bodyNode.put("remain_quota", 0);
            bodyNode.put("remain_amount", 0);
            bodyNode.put("expired_time", -1);
            bodyNode.put("unlimited_quota", true);
            bodyNode.put("model_limits_enabled", false);
            bodyNode.put("model_limits", "");
            bodyNode.put("cross_group_retry", false);
            bodyNode.put("name", "default");
            bodyNode.put("group", "");
            bodyNode.put("allow_ips", "");

            Request request = buildManagementRequestBuilder(buildBaseUrl("/api/token/"), newApiUserId, sessionValue)
                    .post(RequestBody.create(objectMapper.writeValueAsString(bodyNode), JSON_MEDIA_TYPE))
                    .build();
            executeJson(request, "NewAPI 创建 default 令牌");
        } catch (IOException e) {
            throw new BusinessException(500, "NewAPI 创建 default 令牌请求构造失败");
        }
    }

    public String getTokenPlainKey(Long newApiUserId, String sessionValue, Long tokenId) {
        if (tokenId == null) {
            throw new BusinessException(400, "NewAPI 令牌ID不能为空");
        }
        Request request = buildManagementRequestBuilder(
                buildBaseUrl("/api/token/" + tokenId + "/key"),
                newApiUserId,
                sessionValue
        ).post(RequestBody.create("{}", JSON_MEDIA_TYPE)).build();
        JsonNode root = executeJson(request, "NewAPI 获取令牌明文");
        JsonNode dataNode = extractDataNode(root);
        String key = firstText(dataNode.path("key"), root.path("key"));
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(500, "NewAPI 返回令牌明文为空");
        }
        return key;
    }

    private JsonNode executeJson(Request request, String operation) {
        return newApiHttpClient.executeJson(request, operation);
    }

    private Request.Builder buildManagementRequestBuilder(String url, Long newApiUserId, String sessionValue) {
        if (newApiUserId == null) {
            throw new BusinessException(400, "缺少 NewAPI 用户ID，无法调用令牌管理接口");
        }
        if (!StringUtils.hasText(sessionValue)) {
            throw new BusinessException(400, "缺少 NewAPI 登录 session，无法调用令牌管理接口");
        }
        return newApiHttpClient.userJsonRequest(url, newApiUserId, sessionValue);
    }

    private List<NewApiToken> parseTokenItems(JsonNode root) {
        JsonNode dataNode = extractDataNode(root);
        JsonNode items = dataNode.path("items");
        if (items == null || !items.isArray()) {
            return List.of();
        }
        List<NewApiToken> tokens = new ArrayList<>();
        for (JsonNode item : items) {
            Long id = asLongOrNull(item.path("id"));
            if (id == null) {
                continue;
            }
            tokens.add(NewApiToken.builder()
                    .id(id)
                    .name(firstText(item.path("name")))
                    .key(firstText(item.path("key")))
                    .build());
        }
        return tokens;
    }

    private JsonNode parseBody(String body, String operation) throws IOException {
        if (!StringUtils.hasText(body)) {
            throw new BusinessException(500, operation + "返回为空");
        }
        return objectMapper.readTree(body);
    }

    private void checkNewApiSuccess(JsonNode root, String operation) {
        if (root == null || root.isNull()) {
            throw new BusinessException(500, operation + "返回异常");
        }

        if (root.has("success") && !root.path("success").asBoolean(true)) {
            String message = firstText(root.path("message"), root.path("msg"));
            throw new BusinessException(400, StringUtils.hasText(message) ? message : operation + "失败");
        }

        if (root.has("code")) {
            int code = root.path("code").asInt(0);
            if (code != 0 && code != 200) {
                String message = firstText(root.path("msg"), root.path("message"));
                throw new BusinessException(400, StringUtils.hasText(message) ? message : operation + "失败");
            }
        }
    }

    private JsonNode extractDataNode(JsonNode root) {
        JsonNode dataNode = root.path("data");
        if (dataNode == null || dataNode.isMissingNode() || dataNode.isNull()) {
            return root;
        }
        return dataNode;
    }

    private String buildBaseUrl(String path) {
        return newApiHttpClient.buildUrl(path);
    }

    private String extractCookieValue(List<String> setCookieValues, String cookieName) {
        if (setCookieValues == null || setCookieValues.isEmpty()) {
            return null;
        }
        String prefix = cookieName + "=";
        for (String cookie : setCookieValues) {
            if (!StringUtils.hasText(cookie)) {
                continue;
            }
            String[] segments = cookie.split(";");
            for (String segment : segments) {
                String trimmed = segment.trim();
                if (trimmed.startsWith(prefix)) {
                    String value = trimmed.substring(prefix.length());
                    if (StringUtils.hasText(value)) {
                        return value;
                    }
                }
            }
        }
        return null;
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

    private Long parseLongOrNull(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Boolean fetchRemoteEmailVerificationEnabled() {
        Request request = newApiHttpClient.request("/api/status")
                .get()
                .build();

        try {
            NewApiHttpClient.NewApiResponse response = newApiHttpClient.execute(request, "NewAPI 状态探测");
            if (!response.isSuccessful()) {
                return null;
            }
            String body = response.body();
            if (!StringUtils.hasText(body)) {
                return null;
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode dataNode = extractDataNode(root);

            Boolean resolved = firstBoolean(
                    dataNode.path("email_verification"),
                    dataNode.path("email_verification_enabled"),
                    root.path("email_verification"),
                    root.path("email_verification_enabled")
            );
            return resolved;
        } catch (Exception e) {
            log.warn("探测 NewAPI 邮箱认证状态失败: {}", e.getMessage());
            return null;
        }
    }

    private Boolean firstBoolean(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            Boolean parsed = parseBoolean(node);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private Boolean parseBoolean(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.asInt() != 0;
        }
        String text = node.asText(null);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim().toLowerCase();
        if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized)
                || "on".equals(normalized)) {
            return true;
        }
        if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized)
                || "off".equals(normalized)) {
            return false;
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    @Getter
    @Builder
    public static class NewApiLoginResult {
        private Long userId;
        private String username;
        private String token;
        private String sessionValue;
        private String newApiUserHeader;
    }

    @Getter
    @Builder
    public static class NewApiUserProfile {
        private Long id;
        private String username;
        private String nickname;
        private String email;
    }

    @Getter
    @Builder
    public static class NewApiToken {
        private Long id;
        private String name;
        private String key;
    }
}
