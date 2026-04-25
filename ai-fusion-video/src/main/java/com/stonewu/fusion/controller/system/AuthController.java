package com.stonewu.fusion.controller.system;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.controller.system.vo.LoginRespVO;
import com.stonewu.fusion.controller.system.vo.UserRespVO;
import com.stonewu.fusion.entity.system.Role;
import com.stonewu.fusion.entity.system.User;
import com.stonewu.fusion.security.SecurityUserDetails;
import com.stonewu.fusion.security.SecurityUtils;
import com.stonewu.fusion.security.TokenService;
import com.stonewu.fusion.service.system.NewApiAuthService;
import com.stonewu.fusion.service.system.NewApiModelSyncService;
import com.stonewu.fusion.service.system.UserThirdPartyBindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.stonewu.fusion.controller.system.vo.LoginReqVO;
import com.stonewu.fusion.controller.system.vo.RegisterReqVO;
import com.stonewu.fusion.controller.system.vo.ProfileUpdateReqVO;
import com.stonewu.fusion.controller.system.vo.ChangePasswordReqVO;
import com.stonewu.fusion.service.system.UserService;

import java.util.List;

import static com.stonewu.fusion.common.CommonResult.success;

/**
 * 认证控制器
 */
@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UserService userService;
    private final NewApiAuthService newApiAuthService;
    private final UserThirdPartyBindingService userThirdPartyBindingService;
    private final NewApiModelSyncService newApiModelSyncService;

    @PostMapping("/login")
    @Operation(summary = "登录")
    public CommonResult<LoginRespVO> login(@Valid @RequestBody LoginReqVO reqVO) {
        if (!newApiAuthService.isEnabled()) {
            return success(localLogin(reqVO));
        }

        User localUser = userService.getByUsername(reqVO.getUsername());
        if (localUser != null && userService.isAdminUser(localUser.getId())) {
            return success(localLogin(reqVO));
        }

        return success(loginByNewApi(reqVO));
    }

    @PostMapping("/register")
    @Operation(summary = "注册")
    public CommonResult<LoginRespVO> register(@Valid @RequestBody RegisterReqVO reqVO) {
        if (!userService.isSystemInitialized()) {
            throw new BusinessException(400, "系统尚未初始化，请先完成管理员 setup");
        }
        if (newApiAuthService.isEnabled()) {
            return success(registerByNewApi(reqVO));
        }

        User user = userService.register(reqVO.getUsername(), reqVO.getPassword(), reqVO.getNickname());
        TokenService.TokenPair tokenPair = tokenService.createToken(user.getId(), user.getUsername());
        userService.markLoginSuccess(user.getId());
        return success(buildLoginResp(tokenPair, user));
    }

    @GetMapping("/third-party/newapi/status")
    @Operation(summary = "获取 NewAPI 第三方登录状态")
    public CommonResult<NewApiStatusRespVO> getNewApiStatus() {
        boolean enabled = newApiAuthService.isEnabled();
        boolean emailVerificationRequired = enabled && newApiAuthService.isRegisterEmailVerificationRequired();
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean thirdPartyAccountBound = currentUserId != null
                && userThirdPartyBindingService.getNewApiBinding(currentUserId) != null;
        boolean passwordChangeDisabled = isPasswordChangeDisabledForUser(currentUserId, enabled, thirdPartyAccountBound);
        String passwordChangeDisableReason = passwordChangeDisabled
                ? "当前账号已绑定第三方账号，密码由第三方统一管理，本地暂不支持修改密码"
                : null;
        return success(NewApiStatusRespVO.builder()
                .enabled(enabled)
                .emailVerificationEnabled(emailVerificationRequired)
                .userModelConfigDisabled(newApiAuthService.isUserModelConfigDisabled())
                .thirdPartyAccountBound(thirdPartyAccountBound)
                .passwordChangeDisabled(passwordChangeDisabled)
                .passwordChangeDisableReason(passwordChangeDisableReason)
                .build());
    }

    @PostMapping("/third-party/newapi/send-verification")
    @Operation(summary = "发送 NewAPI 邮箱验证码")
    public CommonResult<Boolean> sendNewApiVerification(@Valid @RequestBody NewApiVerificationReqVO reqVO) {
        if (!newApiAuthService.isEnabled()) {
            throw new BusinessException(400, "第三方登录功能未开启");
        }
        newApiAuthService.sendVerificationCode(reqVO.getEmail(), reqVO.getTurnstile());
        return success(true);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌")
    public CommonResult<LoginRespVO> refresh(@Valid @RequestBody RefreshTokenReqVO reqVO) {
        Long userIdFromRefreshToken = tokenService.getUserIdFromRefreshToken(reqVO.getRefreshToken());
        if (userIdFromRefreshToken == null) {
            return CommonResult.error(401, "刷新令牌无效或已过期");
        }
        User user = userService.getById(userIdFromRefreshToken);
        if (user == null) {
            return CommonResult.error(401, "用户不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            tokenService.revokeUserTokens(user.getId());
            return CommonResult.error(403, "用户已被锁定");
        }

        TokenService.TokenPair tokenPair = tokenService.refreshAccessToken(reqVO.getRefreshToken());
        if (tokenPair == null) {
            return CommonResult.error(401, "刷新令牌无效或已过期");
        }

        userService.markLoginSuccess(user.getId());
        user = userService.getById(user.getId());
        return success(buildLoginResp(tokenPair, user));
    }

    @PostMapping("/logout")
    @Operation(summary = "登出")
    public CommonResult<Boolean> logout(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        if (StringUtils.hasText(token)) {
            tokenService.removeToken(token);
        }
        return success(true);
    }

    @GetMapping("/user-info")
    @Operation(summary = "获取当前用户信息")
    public CommonResult<UserRespVO> getUserInfo() {
        SecurityUserDetails userDetails = (SecurityUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        User user = userService.getById(userDetails.getUserId());
        List<Role> roles = userService.getUserRoles(user.getId());

        UserRespVO respVO = new UserRespVO();
        respVO.setId(user.getId());
        respVO.setUsername(user.getUsername());
        respVO.setNickname(user.getNickname());
        respVO.setAvatar(user.getAvatar());
        respVO.setEmail(user.getEmail());
        respVO.setPhone(user.getPhone());
        respVO.setStatus(user.getStatus());
        respVO.setCreateTime(user.getCreateTime());
        respVO.setLastLoginTime(user.getLastLoginTime());
        respVO.setRoles(roles.stream().map(Role::getCode).toList());
        return success(respVO);
    }

    @PutMapping("/profile")
    @Operation(summary = "更新当前用户的个人资料")
    public CommonResult<Boolean> updateProfile(@Valid @RequestBody ProfileUpdateReqVO reqVO) {
        SecurityUserDetails userDetails = (SecurityUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        userService.updateProfile(userDetails.getUserId(), reqVO.getNickname(), reqVO.getEmail(), reqVO.getPhone());
        return success(true);
    }

    @PutMapping("/change-password")
    @Operation(summary = "修改当前用户密码")
    public CommonResult<Boolean> changePassword(@Valid @RequestBody ChangePasswordReqVO reqVO) {
        SecurityUserDetails userDetails = (SecurityUserDetails) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        if (isPasswordChangeDisabledForUser(userDetails.getUserId(), null, null)) {
            throw new BusinessException(400, "当前账号已绑定第三方账号，密码由第三方统一管理，本地暂不支持修改密码");
        }
        userService.changePassword(userDetails.getUserId(), reqVO.getOldPassword(), reqVO.getNewPassword());
        return success(true);
    }

    private boolean isPasswordChangeDisabledForUser(Long userId, Boolean enabledValue, Boolean boundValue) {
        if (userId == null) {
            return false;
        }
        boolean enabled = enabledValue != null ? enabledValue : newApiAuthService.isEnabled();
        if (!enabled) {
            return false;
        }
        if (userService.isAdminUser(userId)) {
            return false;
        }
        boolean bound = boundValue != null ? boundValue : userThirdPartyBindingService.getNewApiBinding(userId) != null;
        return bound;
    }

    /**
     * 构建登录响应
     */
    private LoginRespVO buildLoginResp(TokenService.TokenPair tokenPair, User user) {
        return LoginRespVO.builder()
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .expiresIn(tokenPair.getExpiresIn())
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }

    private LoginRespVO localLogin(LoginReqVO reqVO) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(reqVO.getUsername(), reqVO.getPassword())
        );
        SecurityUserDetails userDetails = (SecurityUserDetails) authentication.getPrincipal();
        TokenService.TokenPair tokenPair = tokenService.createToken(userDetails.getUserId(), userDetails.getUsername());
        userService.markLoginSuccess(userDetails.getUserId());
        User user = userService.getById(userDetails.getUserId());
        return buildLoginResp(tokenPair, user);
    }

    private LoginRespVO loginByNewApi(LoginReqVO reqVO) {
        NewApiAuthService.NewApiLoginResult loginResult = newApiAuthService.login(
                reqVO.getUsername(),
                reqVO.getPassword(),
                reqVO.getTurnstile()
        );
        NewApiAuthService.NewApiUserProfile profile = fetchNewApiProfileWithFallback(
                loginResult,
                reqVO.getUsername(),
                null,
                null
        );

        String username = firstNonBlank(profile.getUsername(), loginResult.getUsername(), reqVO.getUsername());
        String email = trimToNull(profile.getEmail());

        User user = userService.upsertThirdPartyUser(
                username,
                null,
                firstNonBlank(profile.getNickname(), username),
                email
        );
        userThirdPartyBindingService.bindNewApiSession(
                user.getId(),
                profile.getId() != null ? profile.getId() : loginResult.getUserId(),
                username,
                email,
                loginResult.getSessionValue()
        );
        newApiModelSyncService.syncAfterLogin(user, profile.getId() != null ? profile.getId() : loginResult.getUserId());

        userService.markLoginSuccess(user.getId());
        TokenService.TokenPair tokenPair = tokenService.createToken(user.getId(), user.getUsername());
        user = userService.getById(user.getId());
        return buildLoginResp(tokenPair, user);
    }

    private LoginRespVO registerByNewApi(RegisterReqVO reqVO) {
        if (!StringUtils.hasText(reqVO.getEmail())) {
            throw new BusinessException(400, "第三方注册请填写邮箱");
        }
        boolean emailVerificationRequired = newApiAuthService.isRegisterEmailVerificationRequired();
        if (emailVerificationRequired && !StringUtils.hasText(reqVO.getEmailCode())) {
            throw new BusinessException(400, "当前配置要求填写邮箱验证码");
        }

        try {
            newApiAuthService.register(
                    reqVO.getUsername(),
                    reqVO.getPassword(),
                    reqVO.getNickname(),
                    reqVO.getEmail(),
                    reqVO.getEmailCode(),
                    reqVO.getTurnstile()
            );
        } catch (BusinessException ex) {
            if (isEmailVerificationRequiredMessage(ex.getMessage())) {
                throw new BusinessException(400, "当前配置要求填写邮箱验证码");
            }
            throw ex;
        }

        NewApiAuthService.NewApiLoginResult loginResult = newApiAuthService.login(
                reqVO.getUsername(),
                reqVO.getPassword(),
                reqVO.getTurnstile()
        );
        NewApiAuthService.NewApiUserProfile profile = fetchNewApiProfileWithFallback(
                loginResult,
                reqVO.getUsername(),
                reqVO.getNickname(),
                reqVO.getEmail()
        );

        String username = firstNonBlank(profile.getUsername(), loginResult.getUsername(), reqVO.getUsername());
        String email = trimToNull(firstNonBlank(profile.getEmail(), reqVO.getEmail()));
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(400, "第三方账号缺少邮箱信息，无法同步到本地");
        }

        User user = userService.upsertThirdPartyUser(
                username,
                reqVO.getPassword(),
                firstNonBlank(profile.getNickname(), reqVO.getNickname(), username),
                email
        );
        userThirdPartyBindingService.bindNewApiSession(
                user.getId(),
                profile.getId() != null ? profile.getId() : loginResult.getUserId(),
                username,
                email,
                loginResult.getSessionValue()
        );
        newApiModelSyncService.syncAfterLogin(user, profile.getId() != null ? profile.getId() : loginResult.getUserId());

        userService.markLoginSuccess(user.getId());
        TokenService.TokenPair tokenPair = tokenService.createToken(user.getId(), user.getUsername());
        user = userService.getById(user.getId());
        return buildLoginResp(tokenPair, user);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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

    private boolean isEmailVerificationRequiredMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.trim().toLowerCase();
        if (normalized.contains("email verification is enabled")
                || normalized.contains("please enter email address and verification code")) {
            return true;
        }
        return normalized.contains("邮箱验证") && normalized.contains("验证码");
    }

    private NewApiAuthService.NewApiUserProfile fetchNewApiProfileWithFallback(
            NewApiAuthService.NewApiLoginResult loginResult,
            String fallbackUsername,
            String fallbackNickname,
            String fallbackEmail) {
        try {
            return newApiAuthService.getSelf(
                    loginResult.getToken(),
                    loginResult.getSessionValue(),
                    loginResult.getNewApiUserHeader()
            );
        } catch (BusinessException ex) {
            if (!isMissingNewApiUserHeaderMessage(ex.getMessage())) {
                throw ex;
            }
            String username = firstNonBlank(loginResult.getUsername(), fallbackUsername);
            return NewApiAuthService.NewApiUserProfile.builder()
                    .id(loginResult.getUserId())
                    .username(username)
                    .nickname(firstNonBlank(fallbackNickname, username))
                    .email(trimToNull(fallbackEmail))
                    .build();
        }
    }

    private boolean isMissingNewApiUserHeaderMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.trim().toLowerCase();
        return normalized.contains("new-api-user header not provided");
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken)) {
            if (bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
            return bearerToken;
        }
        return request.getParameter("access_token");
    }

    /**
     * 刷新令牌请求
     */
    @Data
    public static class RefreshTokenReqVO {
        @NotBlank(message = "刷新令牌不能为空")
        private String refreshToken;
    }

    @Data
    @Builder
    public static class NewApiStatusRespVO {
        private Boolean enabled;
        private Boolean emailVerificationEnabled;
        private Boolean userModelConfigDisabled;
        private Boolean thirdPartyAccountBound;
        private Boolean passwordChangeDisabled;
        private String passwordChangeDisableReason;
    }

    @Data
    public static class NewApiVerificationReqVO {
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        private String email;

        private String turnstile;
    }
}
