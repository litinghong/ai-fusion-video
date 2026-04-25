package com.stonewu.fusion.service.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.system.UserThirdPartyBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewApiHttpClient {

    public static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_COOKIE = "Cookie";
    private static final String HEADER_NEW_API_USER = "new-api-user";

    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;

    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();

    public String buildUrl(String pathOrUrl) {
        if (!StringUtils.hasText(pathOrUrl)) {
            throw new BusinessException(500, "NewAPI 请求地址不能为空");
        }
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl;
        }
        String baseUrl = systemConfigService.getThirdPartyNewApiBaseUrl();
        if (!StringUtils.hasText(baseUrl) || (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://"))) {
            throw new BusinessException(500, "NewAPI 地址配置无效: " + baseUrl);
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalized + (pathOrUrl.startsWith("/") ? pathOrUrl : "/" + pathOrUrl);
    }

    public Request.Builder request(String pathOrUrl) {
        return new Request.Builder().url(buildUrl(pathOrUrl));
    }

    public Request.Builder jsonRequest(String pathOrUrl) {
        return request(pathOrUrl).header(HEADER_CONTENT_TYPE, "application/json");
    }

    public Request.Builder userJsonRequest(String pathOrUrl, UserThirdPartyBinding binding) {
        if (binding == null) {
            throw new BusinessException(400, "当前账号未绑定 NewAPI 用户");
        }
        return userJsonRequest(pathOrUrl, binding.getThirdPartyUserId(), binding.getSessionValue());
    }

    public Request.Builder userJsonRequest(String pathOrUrl, Long newApiUserId, String sessionValue) {
        return userJsonRequest(pathOrUrl, newApiUserId == null ? null : String.valueOf(newApiUserId), sessionValue);
    }

    public Request.Builder userJsonRequest(String pathOrUrl, String newApiUserHeader, String sessionValue) {
        if (!StringUtils.hasText(newApiUserHeader)) {
            throw new BusinessException(400, "new-api-user header not provided");
        }
        if (!StringUtils.hasText(sessionValue)) {
            throw new BusinessException(400, "缺少 NewAPI 登录 session，无法调用用户接口");
        }
        return jsonRequest(pathOrUrl)
                .header(HEADER_NEW_API_USER, newApiUserHeader.trim())
                .header(HEADER_COOKIE, buildSessionCookieHeader(sessionValue));
    }

    public JsonNode executeJson(Request request, String operation) {
        NewApiResponse response = execute(request, operation);
        if (!StringUtils.hasText(response.body())) {
            throw new BusinessException(500, operation + "返回为空");
        }
        JsonNode root = parseBody(response.body(), operation);
        if (!response.isSuccessful()) {
            throw new BusinessException(response.code(), extractErrorMessage(root, operation));
        }
        checkNewApiSuccess(root, operation);
        return root;
    }

    public NewApiResponse execute(Request request, String operation) {
        logNewApiRequest(request, operation);
        try (Response response = okHttpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            logNewApiResponse(operation, request, response, body);
            return new NewApiResponse(response.code(), response.message(), response.headers(), body);
        } catch (IOException e) {
            log.error("{} 请求失败", operation, e);
            throw new BusinessException(500, operation + "失败");
        }
    }

    public JsonNode extractDataNode(JsonNode root) {
        JsonNode dataNode = root.path("data");
        if (dataNode == null || dataNode.isMissingNode() || dataNode.isNull()) {
            return root;
        }
        return dataNode;
    }

    public String buildSessionCookieHeader(String sessionValue) {
        String raw = sessionValue == null ? null : sessionValue.trim();
        if (StringUtils.hasText(raw) && raw.regionMatches(true, 0, "session=", 0, "session=".length())) {
            raw = raw.substring("session=".length());
        }
        if (StringUtils.hasText(raw)) {
            int semicolonIndex = raw.indexOf(';');
            if (semicolonIndex >= 0) {
                raw = raw.substring(0, semicolonIndex).trim();
            }
        }
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(400, "NewAPI session 无效");
        }
        return "session=" + raw;
    }

    private JsonNode parseBody(String body, String operation) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new BusinessException(500, operation + "返回不是合法 JSON");
        }
    }

    private void checkNewApiSuccess(JsonNode root, String operation) {
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

    private void logNewApiRequest(Request request, String operation) {
        if (request == null) {
            return;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        Headers requestHeaders = request.headers();
        for (int i = 0; i < requestHeaders.size(); i++) {
            String name = requestHeaders.name(i);
            String value = requestHeaders.value(i);
            headers.put(name, maskSensitiveHeader(name, value));
        }
        log.info("[NewAPI][REQ] op={}, method={}, url={}, headers={}, body={}",
                operation,
                request.method(),
                request.url(),
                headers,
                extractRequestBody(request));
    }

    private void logNewApiResponse(String operation, Request request, Response response, String body) {
        if (response == null) {
            return;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        Headers responseHeaders = response.headers();
        for (int i = 0; i < responseHeaders.size(); i++) {
            String name = responseHeaders.name(i);
            String value = responseHeaders.value(i);
            headers.put(name, maskSensitiveHeader(name, value));
        }
        log.info("[NewAPI][RESP] op={}, method={}, url={}, status={}, headers={}, body={}",
                operation,
                request != null ? request.method() : null,
                request != null ? request.url() : null,
                response.code(),
                headers,
                body);
    }

    private String extractRequestBody(Request request) {
        if (request == null || request.body() == null) {
            return "";
        }
        try {
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (Exception ex) {
            return "<unavailable:" + ex.getClass().getSimpleName() + ">";
        }
    }

    private String maskSensitiveHeader(String name, String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String normalizedName = name == null ? "" : name.trim().toLowerCase();
        if ("authorization".equals(normalizedName)) {
            return maskTokenValue(value);
        }
        if ("cookie".equals(normalizedName) || "set-cookie".equals(normalizedName)) {
            return maskCookieValue(value);
        }
        return value;
    }

    private String maskTokenValue(String raw) {
        String text = raw.trim();
        int firstBlank = text.indexOf(' ');
        if (firstBlank <= 0 || firstBlank >= text.length() - 1) {
            return maskKeepEnds(text, 8, 4);
        }
        String prefix = text.substring(0, firstBlank);
        String token = text.substring(firstBlank + 1);
        return prefix + " " + maskKeepEnds(token, 8, 4);
    }

    private String maskCookieValue(String raw) {
        String[] parts = raw.split(";");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.regionMatches(true, 0, "session=", 0, "session=".length())) {
                String session = part.substring("session=".length());
                part = "session=" + maskKeepEnds(session, 8, 4);
            }
            if (i > 0) {
                builder.append("; ");
            }
            builder.append(part);
        }
        return builder.toString();
    }

    private String maskKeepEnds(String value, int keepStart, int keepEnd) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= keepStart + keepEnd) {
            return "***";
        }
        return trimmed.substring(0, keepStart) + "****" + trimmed.substring(trimmed.length() - keepEnd);
    }

    public record NewApiResponse(int code, String message, Headers headers, String body) {
        public boolean isSuccessful() {
            return code >= 200 && code < 300;
        }
    }
}
