package com.stonewu.fusion.service.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.system.UserThirdPartyBinding;
import com.stonewu.fusion.mapper.system.UserThirdPartyBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserThirdPartyBindingService {

    public static final String PROVIDER_NEW_API = "newapi";

    private final UserThirdPartyBindingMapper userThirdPartyBindingMapper;

    public UserThirdPartyBinding getNewApiBinding(Long userId) {
        if (userId == null) {
            return null;
        }
        return userThirdPartyBindingMapper.selectOne(
                new LambdaQueryWrapper<UserThirdPartyBinding>()
                        .eq(UserThirdPartyBinding::getUserId, userId)
                        .eq(UserThirdPartyBinding::getProvider, PROVIDER_NEW_API)
                        .last("LIMIT 1"));
    }

    @Transactional
    public void bindNewApiSession(Long userId,
                                  Long thirdPartyUserId,
                                  String thirdPartyUsername,
                                  String thirdPartyEmail,
                                  String sessionValue) {
        if (userId == null) {
            throw new BusinessException(400, "绑定失败：缺少本地用户信息");
        }

        if (thirdPartyUserId != null) {
            UserThirdPartyBinding boundToOtherUser = userThirdPartyBindingMapper.selectOne(
                    new LambdaQueryWrapper<UserThirdPartyBinding>()
                            .eq(UserThirdPartyBinding::getProvider, PROVIDER_NEW_API)
                            .eq(UserThirdPartyBinding::getThirdPartyUserId, thirdPartyUserId)
                            .last("LIMIT 1"));
            if (boundToOtherUser != null && !boundToOtherUser.getUserId().equals(userId)) {
                throw new BusinessException(400, "第三方账号已绑定其他用户");
            }
        }

        UserThirdPartyBinding currentBinding = getNewApiBinding(userId);

        if (currentBinding == null) {
            userThirdPartyBindingMapper.insert(UserThirdPartyBinding.builder()
                    .userId(userId)
                    .provider(PROVIDER_NEW_API)
                    .thirdPartyUserId(thirdPartyUserId)
                    .thirdPartyUsername(thirdPartyUsername)
                    .thirdPartyEmail(thirdPartyEmail)
                    .sessionValue(sessionValue)
                    .build());
            return;
        }

        currentBinding.setThirdPartyUserId(thirdPartyUserId);
        currentBinding.setThirdPartyUsername(thirdPartyUsername);
        currentBinding.setThirdPartyEmail(thirdPartyEmail);
        currentBinding.setSessionValue(sessionValue);
        userThirdPartyBindingMapper.updateById(currentBinding);
    }

    @Transactional
    public void bindNewApiDefaultToken(Long userId, Long tokenId, String tokenKey) {
        if (userId == null) {
            throw new BusinessException(400, "绑定失败：缺少本地用户信息");
        }
        if (tokenId == null || !StringUtils.hasText(tokenKey)) {
            throw new BusinessException(400, "绑定失败：缺少 NewAPI 令牌信息");
        }

        UserThirdPartyBinding binding = getNewApiBinding(userId);
        if (binding == null) {
            binding = UserThirdPartyBinding.builder()
                    .userId(userId)
                    .provider(PROVIDER_NEW_API)
                    .build();
            userThirdPartyBindingMapper.insert(binding);
        }
        binding.setNewapiTokenId(tokenId);
        binding.setNewapiTokenKey(tokenKey);
        userThirdPartyBindingMapper.updateById(binding);
    }
}
