package com.stonewu.fusion.controller.wallet.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlipayPayRespVO {

    private String checkoutUrl;

    private String orderId;

    private String amount;

    private Long creditedAmount;
}
