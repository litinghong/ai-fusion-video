package com.stonewu.fusion.controller.wallet.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlipayAmountRespVO {

    private String minAmount;

    private String currency;

    private List<ReferenceAmount> referenceAmounts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferenceAmount {
        private String amount;

        private String currency;

        private String productId;

        private String name;

        private Long quota;
    }
}
