package com.stonewu.fusion.controller.system;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.common.PageResult;
import com.stonewu.fusion.controller.system.vo.UserPageReqVO;
import com.stonewu.fusion.controller.system.vo.UserResetPasswordRespVO;
import com.stonewu.fusion.controller.system.vo.UserRespVO;
import com.stonewu.fusion.controller.system.vo.UserSetRolesReqVO;
import com.stonewu.fusion.convert.system.UserConvert;
import com.stonewu.fusion.entity.system.Role;
import com.stonewu.fusion.entity.system.User;
import com.stonewu.fusion.service.system.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.stonewu.fusion.common.CommonResult.success;
import static com.stonewu.fusion.security.SecurityUtils.requireCurrentUserId;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/page")
    @Operation(summary = "获取用户分页列表")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<PageResult<UserRespVO>> getUserPage(@Valid UserPageReqVO reqVO) {
        return success(userService.getPage(reqVO.getUsername(), reqVO.getNickname(),
                reqVO.getStatus(), reqVO.getPageNo(), reqVO.getPageSize())
                .map(this::enrichUserVO));
    }

    @GetMapping("/get")
    @Operation(summary = "获取用户详情")
    @Parameter(name = "id", description = "用户ID", required = true)
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<UserRespVO> getUser(@RequestParam("id") Long id) {
        User user = userService.getById(id);
        return success(user == null ? null : enrichUserVO(user));
    }

    @PutMapping("/update")
    @Operation(summary = "更新用户")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<Boolean> updateUser(@RequestParam("id") Long id,
                                            @RequestParam(value = "nickname", required = false) String nickname,
                                            @RequestParam(value = "avatar", required = false) String avatar,
                                            @RequestParam(value = "email", required = false) String email,
                                            @RequestParam(value = "phone", required = false) String phone,
                                            @RequestParam(value = "status", required = false) Integer status) {
        userService.updateUser(id, nickname, avatar, email, phone, status, requireCurrentUserId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除用户")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<Boolean> deleteUser(@RequestParam("id") Long id) {
        userService.deleteUser(id, requireCurrentUserId());
        return success(true);
    }

    @PostMapping("/assign-role")
    @Operation(summary = "分配角色")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<Boolean> assignRole(@RequestParam("userId") Long userId,
                                            @RequestParam("roleId") Long roleId) {
        userService.assignRole(userId, roleId, requireCurrentUserId());
        return success(true);
    }

    @PostMapping("/remove-role")
    @Operation(summary = "移除角色")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<Boolean> removeRole(@RequestParam("userId") Long userId,
                                            @RequestParam("roleId") Long roleId) {
        userService.removeRole(userId, roleId, requireCurrentUserId());
        return success(true);
    }

    @PostMapping("/set-roles")
    @Operation(summary = "批量设置用户角色")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<Boolean> setRoles(@Valid @RequestBody UserSetRolesReqVO reqVO) {
        userService.setUserRoles(reqVO.getUserId(), reqVO.getRoleCodes(), requireCurrentUserId());
        return success(true);
    }

    @PostMapping("/lock")
    @Operation(summary = "锁定用户")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<Boolean> lockUser(@RequestParam("id") Long id) {
        userService.lockUser(id, requireCurrentUserId());
        return success(true);
    }

    @PostMapping("/unlock")
    @Operation(summary = "解锁用户")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<Boolean> unlockUser(@RequestParam("id") Long id) {
        userService.unlockUser(id);
        return success(true);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "重置用户密码")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<UserResetPasswordRespVO> resetPassword(@RequestParam("id") Long id) {
        String temporaryPassword = userService.resetPassword(id, requireCurrentUserId());
        return success(new UserResetPasswordRespVO(temporaryPassword));
    }

    @GetMapping("/roles")
    @Operation(summary = "获取可分配角色列表")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<List<String>> getAssignableRoles() {
        return success(List.of("admin", "user"));
    }

    /**
     * MapStruct 自动映射基础字段 + 手动补充 roles
     */
    private UserRespVO enrichUserVO(User user) {
        UserRespVO vo = UserConvert.INSTANCE.convert(user);
        List<Role> roles = userService.getUserRoles(user.getId());
        vo.setRoles(roles.stream().map(Role::getCode).toList());
        return vo;
    }
}
