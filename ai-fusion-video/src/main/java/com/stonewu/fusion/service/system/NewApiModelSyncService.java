package com.stonewu.fusion.service.system;

import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.controller.ai.vo.RemoteModelVO;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.entity.system.User;
import com.stonewu.fusion.entity.system.UserThirdPartyBinding;
import com.stonewu.fusion.service.ai.AiModelService;
import com.stonewu.fusion.service.ai.ApiConfigService;
import com.stonewu.fusion.service.ai.provider.AiProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewApiModelSyncService {

    private static final String DEFAULT_TOKEN_NAME = "default";

    private final SystemConfigService systemConfigService;
    private final NewApiAuthService newApiAuthService;
    private final UserThirdPartyBindingService userThirdPartyBindingService;
    private final ApiConfigService apiConfigService;
    private final AiModelService aiModelService;
    private final AiProviderService aiProviderService;

    /**
     * 登录成功后同步 NewAPI default 令牌、按用户自动维护 API 配置，并尽力同步远程模型。
     * <p>
     * 失败策略：
     * - 令牌链路失败：抛出异常阻断登录
     * - 模型同步失败：记录日志，不阻断登录
     */
    public void syncAfterLogin(User user, Long newApiUserId) {
        if (user == null || user.getId() == null) {
            log.info("[NewAPI][SYNC] skip: user is null");
            return;
        }
        boolean newApiEnabled = newApiAuthService.isEnabled();
        boolean modelSyncEnabled = systemConfigService.isThirdPartyNewApiModelSyncEnabled();
        if (!newApiEnabled || !modelSyncEnabled) {
            log.info("[NewAPI][SYNC] skip: userId={}, newApiEnabled={}, modelSyncEnabled={}",
                    user.getId(), newApiEnabled, modelSyncEnabled);
            return;
        }
        if (newApiUserId == null) {
            throw new BusinessException(500, "NewAPI 登录成功但未返回用户ID，无法同步个人令牌");
        }
        log.info("[NewAPI][SYNC] start: userId={}, newApiUserId={}", user.getId(), newApiUserId);

        UserThirdPartyBinding binding = userThirdPartyBindingService.getNewApiBinding(user.getId());
        if (binding == null) {
            throw new BusinessException(500, "NewAPI 绑定信息不存在，无法同步个人令牌");
        }
        Long resolvedNewApiUserId = binding.getThirdPartyUserId() != null ? binding.getThirdPartyUserId() : newApiUserId;
        if (resolvedNewApiUserId == null) {
            throw new BusinessException(500, "NewAPI 登录成功但未返回用户ID，无法同步个人令牌");
        }
        if (!StringUtils.hasText(binding.getSessionValue())) {
            throw new BusinessException(500, "NewAPI 登录成功但未返回 session，无法同步个人令牌");
        }
        log.info("[NewAPI][SYNC] binding: userId={}, thirdPartyUserId={}, hasSession={}",
                user.getId(), resolvedNewApiUserId, true);

        NewApiAuthService.NewApiToken token = ensureDefaultToken(
                resolvedNewApiUserId, binding.getSessionValue());
        log.info("[NewAPI][SYNC] token resolved: userId={}, tokenId={}, tokenName={}",
                user.getId(), token.getId(), token.getName());
        String tokenPlainKey = newApiAuthService.getTokenPlainKey(
                resolvedNewApiUserId, binding.getSessionValue(), token.getId());
        userThirdPartyBindingService.bindNewApiDefaultToken(user.getId(), token.getId(), tokenPlainKey);

        ApiConfig apiConfig = apiConfigService.upsertNewApiConfig(
                user.getId(),
                newApiAuthService.getBaseUrl(),
                tokenPlainKey
        );

        try {
            syncRemoteModels(user.getId(), apiConfig);
        } catch (Exception ex) {
            log.warn("NewAPI 模型自动同步失败(userId={}): {}", user.getId(), ex.getMessage());
        }
        log.info("[NewAPI][SYNC] done: userId={}, apiConfigId={}", user.getId(), apiConfig.getId());
    }

    private NewApiAuthService.NewApiToken ensureDefaultToken(Long newApiUserId,
                                                             String sessionValue) {
        List<NewApiAuthService.NewApiToken> tokens =
                newApiAuthService.listTokens(newApiUserId, sessionValue);
        NewApiAuthService.NewApiToken matched = findDefaultToken(tokens);
        if (matched != null) {
            return matched;
        }

        newApiAuthService.createDefaultToken(newApiUserId, sessionValue);
        List<NewApiAuthService.NewApiToken> refreshedTokens =
                newApiAuthService.listTokens(newApiUserId, sessionValue);
        matched = findDefaultToken(refreshedTokens);
        if (matched == null) {
            throw new BusinessException(500, "NewAPI default 令牌创建后仍未找到");
        }
        return matched;
    }

    private NewApiAuthService.NewApiToken findDefaultToken(List<NewApiAuthService.NewApiToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        return tokens.stream()
                .filter(token -> DEFAULT_TOKEN_NAME.equals(token.getName()))
                .filter(token -> token.getId() != null)
                .findFirst()
                .orElse(null);
    }

    private void syncRemoteModels(Long userId, ApiConfig apiConfig) {
        if (apiConfig == null || apiConfig.getId() == null) {
            return;
        }
        List<RemoteModelVO> remoteModels = aiProviderService.listRemoteModels(apiConfig);
        log.info("[NewAPI][SYNC] remote models fetched: userId={}, apiConfigId={}, count={}",
                userId, apiConfig.getId(), remoteModels != null ? remoteModels.size() : 0);
        for (RemoteModelVO remoteModel : remoteModels) {
            if (remoteModel == null || !StringUtils.hasText(remoteModel.getId())) {
                continue;
            }
            int modelType = remoteModel.getModelType() != null ? remoteModel.getModelType() : 1;
            try {
                aiModelService.upsertModelByCodeForUser(userId, apiConfig.getId(), remoteModel.getId(), modelType);
            } catch (Exception ex) {
                log.warn("NewAPI 模型导入失败(userId={}, model={}): {}", userId, remoteModel.getId(), ex.getMessage());
            }
        }
    }
}
