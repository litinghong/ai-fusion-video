package com.stonewu.fusion.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.controller.system.vo.LoginRespVO;
import com.stonewu.fusion.entity.system.Role;
import com.stonewu.fusion.entity.system.User;
import com.stonewu.fusion.entity.system.UserRole;
import com.stonewu.fusion.mapper.system.RoleMapper;
import com.stonewu.fusion.mapper.system.UserMapper;
import com.stonewu.fusion.mapper.system.UserRoleMapper;
import com.stonewu.fusion.security.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

import static com.stonewu.fusion.common.CommonResult.success;

/**
 * 系统初始化控制器
 */
@Tag(name = "系统初始化")
@RestController
@RequestMapping("/system/init")
@RequiredArgsConstructor
public class SystemInitController {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @GetMapping("/status")
    @Operation(summary = "获取系统初始化状态")
    public CommonResult<Map<String, Boolean>> getInitStatus() {
        Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "admin"));
        boolean initialized = false;
        if (adminRole != null) {
            initialized = userRoleMapper.exists(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, adminRole.getId()));
        }
        return success(Map.of("initialized", initialized));
    }

    @PostMapping("/setup")
    @Operation(summary = "初始化管理员账号")
    public CommonResult<LoginRespVO> setup(@Valid @RequestBody SetupReqVO reqVO) {
        // 检查是否已初始化
        Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "admin"));
        if (adminRole == null) {
            throw new RuntimeException("系统角色数据异常，请检查数据库初始化");
        }

        if (userRoleMapper.exists(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, adminRole.getId()))) {
            throw new RuntimeException("系统已初始化，请勿重复操作");
        }

        // 创建管理员用户
        User user = User.builder()
                .username(reqVO.getUsername())
                .password(passwordEncoder.encode(reqVO.getPassword()))
                .nickname(reqVO.getNickname() != null ? reqVO.getNickname() : reqVO.getUsername())
                .status(1)
                .lastLoginTime(LocalDateTime.now())
                .build();
        userMapper.insert(user);

        // 分配管理员角色
        userRoleMapper.insert(UserRole.builder()
                .userId(user.getId())
                .roleId(adminRole.getId())
                .build());

        // 同时分配普通用户角色
        Role userRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "user"));
        if (userRole != null) {
            userRoleMapper.insert(UserRole.builder()
                    .userId(user.getId())
                    .roleId(userRole.getId())
                    .build());
        }

        // 自动登录
        TokenService.TokenPair tokenPair = tokenService.createToken(user.getId(), user.getUsername());
        return success(LoginRespVO.builder()
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .expiresIn(tokenPair.getExpiresIn())
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build());
    }

    @Data
    public static class SetupReqVO {
        private String username;
        private String password;
        private String nickname;
    }
}
