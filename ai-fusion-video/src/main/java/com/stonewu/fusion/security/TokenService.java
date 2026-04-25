package com.stonewu.fusion.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 基于 Redis 的 Token 服务
 * <p>
 * 使用 UUID 作为 token，支持 access_token + refresh_token 双 token 机制
 * <ul>
 *   <li>access_token：短期有效（2小时），用于接口鉴权</li>
 *   <li>refresh_token：长期有效（7天），用于刷新 access_token</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String ACCESS_TOKEN_PREFIX = "fusion:token:";
    private static final String REFRESH_TOKEN_PREFIX = "fusion:refresh_token:";
    private static final String USER_TOKEN_PREFIX = "fusion:user_token:";

    /** access_token 有效期：2 小时 */
    private static final long ACCESS_TOKEN_EXPIRE_HOURS = 2;
    /** refresh_token 有效期：7 天 */
    private static final long REFRESH_TOKEN_EXPIRE_DAYS = 7;

    private final StringRedisTemplate redisTemplate;

    /**
     * 令牌对，包含 access_token 和 refresh_token
     */
    @Data
    @AllArgsConstructor
    public static class TokenPair {
        /** 访问令牌 */
        private String accessToken;
        /** 刷新令牌 */
        private String refreshToken;
        /** access_token 有效期（秒） */
        private long expiresIn;
    }

    /**
     * 创建令牌对（登录/注册时调用）
     *
     * @param userId   用户ID
     * @param username 用户名
     * @return 包含 access_token 和 refresh_token 的令牌对
     */
    public TokenPair createToken(Long userId, String username) {
        // 删除该用户之前的 token（单端登录）
        String oldAccessToken = redisTemplate.opsForValue().get(USER_TOKEN_PREFIX + userId);
        if (oldAccessToken != null) {
            // 清除旧 access_token
            redisTemplate.delete(ACCESS_TOKEN_PREFIX + oldAccessToken);
            // 清除旧 refresh_token（通过 access_token 查找）
            String oldRefreshToken = redisTemplate.opsForValue().get(ACCESS_TOKEN_PREFIX + oldAccessToken + ":refresh");
            if (oldRefreshToken != null) {
                redisTemplate.delete(REFRESH_TOKEN_PREFIX + oldRefreshToken);
                redisTemplate.delete(ACCESS_TOKEN_PREFIX + oldAccessToken + ":refresh");
            }
        }

        String userValue = userId + ":" + username;

        // 生成 access_token
        String accessToken = generateUUID();
        redisTemplate.opsForValue().set(
                ACCESS_TOKEN_PREFIX + accessToken,
                userValue,
                Duration.ofHours(ACCESS_TOKEN_EXPIRE_HOURS)
        );

        // 生成 refresh_token
        String refreshToken = generateUUID();
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + refreshToken,
                userValue,
                Duration.ofDays(REFRESH_TOKEN_EXPIRE_DAYS)
        );

        // 记录 access_token 对应的 refresh_token（用于登出时一起清除）
        redisTemplate.opsForValue().set(
                ACCESS_TOKEN_PREFIX + accessToken + ":refresh",
                refreshToken,
                Duration.ofDays(REFRESH_TOKEN_EXPIRE_DAYS)
        );

        // userId -> accessToken（用于单端登录踢出）
        redisTemplate.opsForValue().set(
                USER_TOKEN_PREFIX + userId,
                accessToken,
                Duration.ofDays(REFRESH_TOKEN_EXPIRE_DAYS)
        );

        return new TokenPair(accessToken, refreshToken, ACCESS_TOKEN_EXPIRE_HOURS * 3600);
    }

    /**
     * 使用 refresh_token 刷新 access_token
     *
     * @param refreshToken 刷新令牌
     * @return 新的令牌对；如果 refresh_token 无效则返回 null
     */
    public TokenPair refreshAccessToken(String refreshToken) {
        String value = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + refreshToken);
        if (value == null) {
            return null;
        }

        Long userId = parseUserId(value);
        String username = parseUsername(value);

        // 删除旧的 access_token
        String oldAccessToken = redisTemplate.opsForValue().get(USER_TOKEN_PREFIX + userId);
        if (oldAccessToken != null) {
            redisTemplate.delete(ACCESS_TOKEN_PREFIX + oldAccessToken);
            redisTemplate.delete(ACCESS_TOKEN_PREFIX + oldAccessToken + ":refresh");
        }

        // 删除旧的 refresh_token
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);

        // 生成新的令牌对（refresh_token 也会轮换，更安全）
        String newAccessToken = generateUUID();
        String newRefreshToken = generateUUID();
        String userValue = userId + ":" + username;

        redisTemplate.opsForValue().set(
                ACCESS_TOKEN_PREFIX + newAccessToken,
                userValue,
                Duration.ofHours(ACCESS_TOKEN_EXPIRE_HOURS)
        );

        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + newRefreshToken,
                userValue,
                Duration.ofDays(REFRESH_TOKEN_EXPIRE_DAYS)
        );

        redisTemplate.opsForValue().set(
                ACCESS_TOKEN_PREFIX + newAccessToken + ":refresh",
                newRefreshToken,
                Duration.ofDays(REFRESH_TOKEN_EXPIRE_DAYS)
        );

        redisTemplate.opsForValue().set(
                USER_TOKEN_PREFIX + userId,
                newAccessToken,
                Duration.ofDays(REFRESH_TOKEN_EXPIRE_DAYS)
        );

        return new TokenPair(newAccessToken, newRefreshToken, ACCESS_TOKEN_EXPIRE_HOURS * 3600);
    }

    /**
     * 根据 refresh_token 获取用户ID（用于刷新前的权限/状态校验）
     */
    public Long getUserIdFromRefreshToken(String refreshToken) {
        String value = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + refreshToken);
        if (value == null) {
            return null;
        }
        return parseUserId(value);
    }

    /**
     * 根据 access_token 获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        String value = redisTemplate.opsForValue().get(ACCESS_TOKEN_PREFIX + token);
        if (value == null) {
            return null;
        }
        return parseUserId(value);
    }

    /**
     * 根据 access_token 获取用户名
     */
    public String getUsernameFromToken(String token) {
        String value = redisTemplate.opsForValue().get(ACCESS_TOKEN_PREFIX + token);
        if (value == null) {
            return null;
        }
        return parseUsername(value);
    }

    /**
     * 验证 access_token 是否有效
     */
    public boolean validateToken(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ACCESS_TOKEN_PREFIX + token));
    }

    /**
     * 删除令牌（登出时调用），同时清除 access_token 和 refresh_token
     */
    public void removeToken(String token) {
        Long userId = getUserIdFromToken(token);

        // 清除关联的 refresh_token
        String refreshToken = redisTemplate.opsForValue().get(ACCESS_TOKEN_PREFIX + token + ":refresh");
        if (refreshToken != null) {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
        }

        // 清除 access_token 及其关联键
        redisTemplate.delete(ACCESS_TOKEN_PREFIX + token);
        redisTemplate.delete(ACCESS_TOKEN_PREFIX + token + ":refresh");

        if (userId != null) {
            redisTemplate.delete(USER_TOKEN_PREFIX + userId);
        }
    }

    /**
     * 按用户撤销令牌（用于锁定用户后强制下线）
     */
    public void revokeUserTokens(Long userId) {
        if (userId == null) {
            return;
        }
        String accessToken = redisTemplate.opsForValue().get(USER_TOKEN_PREFIX + userId);
        if (accessToken == null) {
            redisTemplate.delete(USER_TOKEN_PREFIX + userId);
            return;
        }
        removeToken(accessToken);
    }

    private Long parseUserId(String tokenValue) {
        return Long.parseLong(tokenValue.split(":", 2)[0]);
    }

    private String parseUsername(String tokenValue) {
        String[] parts = tokenValue.split(":", 2);
        return parts.length > 1 ? parts[1] : null;
    }

    private String generateUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
