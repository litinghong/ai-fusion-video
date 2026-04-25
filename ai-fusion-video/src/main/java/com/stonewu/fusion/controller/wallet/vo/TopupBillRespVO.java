package com.stonewu.fusion.controller.wallet.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopupBillRespVO {

    private Long id;

    private Long userId;

    private Long amount;

    private Double money;

    private String tradeNo;

    private String paymentMethod;

    private Long createTime;

    private Long completeTime;

    private String status;
}
