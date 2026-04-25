package com.stonewu.fusion.service.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.common.PageResult;
import com.stonewu.fusion.entity.system.Role;
import com.stonewu.fusion.entity.system.User;
import com.stonewu.fusion.entity.system.UserRole;
import com.stonewu.fusion.mapper.system.RoleMapper;
import com.stonewu.fusion.mapper.system.UserMapper;
import com.stonewu.fusion.mapper.system.UserRoleMapper;
import com.stonewu.fusion.security.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_USER = "user";
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional
    public User register(String username, String password, String nickname) {
        boolean exists = userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exists) {
            throw new BusinessException(400, "用户名已存在");
        }
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .nickname(nickname != null ? nickname : username)
                .status(1)
                .lastLoginTime(LocalDateTime.now())
                .build();
        userMapper.insert(user);

        Role defaultRole = getRoleByCode(ROLE_USER, false);
        if (defaultRole != null) {
            userRoleMapper.insert(UserRole.builder().userId(user.getId()).roleId(defaultRole.getId()).build());
        }
        return user;
    }

    /**
     * 系统是否已完成初始化（即已有管理员账号）
     */
    public boolean isSystemInitialized() {
        Role adminRole = getRoleByCode(ROLE_ADMIN, false);
        if (adminRole == null) {
            return false;
        }
        return userRoleMapper.exists(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, adminRole.getId()));
    }

    @Cacheable(value = "userByUsername", key = "#username", unless = "#result == null")
    public User getByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    @Transactional
    @CacheEvict(value = "userByUsername", allEntries = true)
    public User upsertThirdPartyUser(String username,
                                     String rawPasswordForCreate,
                                     String nickname,
                                     String email) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(400, "第三方用户缺少用户名");
        }
        String normalizedEmail = (email != null && !email.isBlank()) ? email.trim() : null;

        User existing = getByUsername(username);
        if (existing == null) {
            String passwordToStore = (rawPasswordForCreate != null && !rawPasswordForCreate.isBlank())
                    ? rawPasswordForCreate
                    : generateTemporaryPassword(20);
            User created = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(passwordToStore))
                    .nickname((nickname != null && !nickname.isBlank()) ? nickname : username)
                    .email(normalizedEmail)
                    .status(1)
                    .lastLoginTime(LocalDateTime.now())
                    .build();
            userMapper.insert(created);

            Role defaultRole = getRoleByCode(ROLE_USER, false);
            if (defaultRole != null) {
                userRoleMapper.insert(UserRole.builder()
                        .userId(created.getId())
                        .roleId(defaultRole.getId())
                        .build());
            }
            return created;
        }

        if (isAdminUser(existing.getId())) {
            throw new BusinessException(400, "管理员账号请使用本地登录");
        }
        if (existing.getStatus() == null || existing.getStatus() != 1) {
            throw new BusinessException(403, "用户已被锁定");
        }

        boolean changed = false;
        if (nickname != null && !nickname.isBlank() && !nickname.equals(existing.getNickname())) {
            existing.setNickname(nickname);
            changed = true;
        }
        if (normalizedEmail != null && !normalizedEmail.equals(existing.getEmail())) {
            existing.setEmail(normalizedEmail);
            changed = true;
        }
        if (changed) {
            userMapper.updateById(existing);
        }
        return existing;
    }

    public PageResult<User> getPage(String username, String nickname, Integer status, int pageNo, int pageSize) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(username != null, User::getUsername, username)
                .like(nickname != null, User::getNickname, nickname)
                .eq(status != null, User::getStatus, status)
                .orderByDesc(User::getId);
        return PageResult.of(userMapper.selectPage(new Page<>(pageNo, pageSize), wrapper));
    }

    @Transactional
    @CacheEvict(value = "userByUsername", allEntries = true)
    public void markLoginSuccess(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional
    @CacheEvict(value = "userByUsername", allEntries = true)
    public void updateUser(Long id, String nickname, String avatar, String email, String phone, Integer status) {
        updateUser(id, nickname, avatar, email, phone, status, null);
    }

    @Transactional
    @CacheEvict(value = "userByUsername", allEntries = true)
    public void updateUser(Long id, String nickname, String avatar, String email, String phone, Integer status, Long operatorUserId) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        if (email != null) {
            user.setEmail(email);
        }
        if (phone != null) {
            user.setPhone(phone);
        }
        if (status != null) {
            if (status == 0) {
                checkCanDisableUser(id, operatorUserId);
                tokenService.revokeUserTokens(id);
            }
            user.setStatus(status);
        }
        userMapper.updateById(user);
    }

    /**
     * 当前用户修改自己的个人资料
     */
    @Transactional
    @CacheEvict(value = "userByUsername", allEntries = true)
    public void updateProfile(Long userId, String nickname, String email, String phone) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (email != null) {
            user.setEmail(email);
        }
        if (phone != null) {
            user.setPhone(phone);
        }
        userMapper.updateById(user);
    }

    /**
     * 修改密码（需验证旧密码）
     */
    @Transactional
    @CacheEvict(value = "userByUsername", allEntries = true)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(400, "旧密码不正确");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    @Transactional
    @CacheEvict(value = { "userByUsername", "userRoles" }, allEntries = true)
    public void deleteUser(Long id) {
        deleteUser(id, null);
    }

    @Transactional
    @CacheEvict(value = { "userByUsername", "userRoles" }, allEntries = true)
    public void deleteUser(Long id, Long operatorUserId) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return;
        }
        if (operatorUserId != null && operatorUserId.equals(id)) {
            throw new BusinessException(400, "不能删除当前登录用户");
        }
        if (isAdminUser(id) && countEnabledAdminUsers() <= 1) {
            throw new BusinessException(400, "系统必须至少保留一个可用管理员");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        userMapper.deleteById(id);
        tokenService.revokeUserTokens(id);
    }

    @Cacheable(value = "userRoles", key = "#userId")
    public List<Role> getUserRoles(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectByIds(roleIds);
    }

    @Transactional
    @CacheEvict(value = "userRoles", key = "#userId")
    public void assignRole(Long userId, Long roleId) {
        assignRole(userId, roleId, null);
    }

    @Transactional
    @CacheEvict(value = "userRoles", key = "#userId")
    public void assignRole(Long userId, Long roleId, Long operatorUserId) {
        checkUserExists(userId);
        checkRoleExists(roleId);
        boolean exists = userRoleMapper.exists(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleId, roleId));
        if (!exists) {
            userRoleMapper.insert(UserRole.builder().userId(userId).roleId(roleId).build());
        }
    }

    @Transactional
    @CacheEvict(value = "userRoles", key = "#userId")
    public void removeRole(Long userId, Long roleId) {
        removeRole(userId, roleId, null);
    }

    @Transactional
    @CacheEvict(value = "userRoles", key = "#userId")
    public void removeRole(Long userId, Long roleId, Long operatorUserId) {
        checkUserExists(userId);
        if (operatorUserId != null && operatorUserId.equals(userId) && isAdminRole(roleId)) {
            throw new BusinessException(400, "不能移除自己的管理员角色");
        }
        if (isAdminRole(roleId) && isAdminUser(userId) && countEnabledAdminUsers() <= 1) {
            throw new BusinessException(400, "系统必须至少保留一个可用管理员");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, roleId));
    }

    @Transactional
    @CacheEvict(value = "userRoles", key = "#userId")
    public void setUserRoles(Long userId, List<String> roleCodes, Long operatorUserId) {
        checkUserExists(userId);
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BusinessException(400, "角色列表不能为空");
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String roleCode : roleCodes) {
            if (roleCode == null || roleCode.isBlank()) {
                continue;
            }
            normalized.add(roleCode.trim().toLowerCase(Locale.ROOT));
        }
        if (normalized.isEmpty()) {
            throw new BusinessException(400, "角色列表不能为空");
        }

        for (String code : normalized) {
            if (!ROLE_ADMIN.equals(code) && !ROLE_USER.equals(code)) {
                throw new BusinessException(400, "不支持的角色: " + code);
            }
        }

        List<Role> targetRoles = new ArrayList<>();
        for (String code : normalized) {
            targetRoles.add(getRoleByCode(code, true));
        }

        boolean currentlyAdmin = isAdminUser(userId);
        boolean willKeepAdmin = normalized.contains(ROLE_ADMIN);

        if (currentlyAdmin && !willKeepAdmin && countEnabledAdminUsers() <= 1) {
            throw new BusinessException(400, "系统必须至少保留一个可用管理员");
        }
        if (operatorUserId != null && operatorUserId.equals(userId) && !willKeepAdmin && currentlyAdmin) {
            throw new BusinessException(400, "不能移除自己的管理员角色");
        }

        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (Role role : targetRoles) {
            userRoleMapper.insert(UserRole.builder().userId(userId).roleId(role.getId()).build());
        }
    }

    @Transactional
    @CacheEvict(value = "userByUsername", allEntries = true)
    public void lockUser(Long userId, Long operatorUserId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        checkCanDisableUser(userId, operatorUserId);
        user.setStatus(0);
        userMapper.updateById(user);
        tokenService.revokeUserTokens(userId);
    }

    @Transactional
    @CacheEvict(value = "userByUsername", allEntries = true)
    public void unlockUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setStatus(1);
        userMapper.updateById(user);
    }

    @Transactional
    @CacheEvict(value = "userByUsername", allEntries = true)
    public String resetPassword(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        String temporaryPassword = generateTemporaryPassword(12);
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        userMapper.updateById(user);
        tokenService.revokeUserTokens(userId);
        return temporaryPassword;
    }

    private void checkCanDisableUser(Long userId, Long operatorUserId) {
        if (operatorUserId != null && operatorUserId.equals(userId)) {
            throw new BusinessException(400, "不能锁定自己");
        }
        if (isAdminUser(userId) && countEnabledAdminUsers() <= 1) {
            throw new BusinessException(400, "系统必须至少保留一个可用管理员");
        }
    }

    private void checkUserExists(Long userId) {
        if (userMapper.selectById(userId) == null) {
            throw new BusinessException(404, "用户不存在");
        }
    }

    private void checkRoleExists(Long roleId) {
        if (roleMapper.selectById(roleId) == null) {
            throw new BusinessException(404, "角色不存在");
        }
    }

    private boolean isAdminRole(Long roleId) {
        Role adminRole = getRoleByCode(ROLE_ADMIN, false);
        return adminRole != null && adminRole.getId().equals(roleId);
    }

    public boolean isAdminUser(Long userId) {
        Role adminRole = getRoleByCode(ROLE_ADMIN, false);
        if (adminRole == null) {
            return false;
        }
        return userRoleMapper.exists(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, adminRole.getId()));
    }

    private long countEnabledAdminUsers() {
        Role adminRole = getRoleByCode(ROLE_ADMIN, false);
        if (adminRole == null) {
            return 0L;
        }
        List<UserRole> adminUserRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, adminRole.getId()));
        if (adminUserRoles.isEmpty()) {
            return 0L;
        }
        List<Long> userIds = adminUserRoles.stream().map(UserRole::getUserId).distinct().toList();
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .in(User::getId, userIds)
                .eq(User::getStatus, 1));
    }

    private Role getRoleByCode(String roleCode, boolean required) {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getCode, roleCode)
                .last("LIMIT 1"));
        if (role == null && required) {
            throw new BusinessException(500, "系统角色数据异常: " + roleCode);
        }
        return role;
    }

    private String generateTemporaryPassword(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = random.nextInt(TEMP_PASSWORD_CHARS.length());
            builder.append(TEMP_PASSWORD_CHARS.charAt(idx));
        }
        return builder.toString();
    }
}
