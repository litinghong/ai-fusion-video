package com.stonewu.fusion.controller.system.vo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UserSetRolesReqVO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotEmpty(message = "角色列表不能为空")
    private List<String> roleCodes;
}
