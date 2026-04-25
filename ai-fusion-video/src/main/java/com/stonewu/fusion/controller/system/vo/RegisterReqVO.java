package com.stonewu.fusion.controller.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "注册请求")
@Data
public class RegisterReqVO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度为 3-32 位")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度为 6-32 位")
    private String password;

    private String nickname;

    @Schema(description = "邮箱（第三方注册启用时使用）")
    private String email;

    @Schema(description = "邮箱验证码（开启邮箱认证时必填）")
    private String emailCode;

    @Schema(description = "可选的 turnstile 验证值")
    private String turnstile;
}
