package com.stonewu.fusion.controller.wallet.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TopupReqVO {

    @NotBlank(message = "兑换码不能为空")
    private String key;
}
