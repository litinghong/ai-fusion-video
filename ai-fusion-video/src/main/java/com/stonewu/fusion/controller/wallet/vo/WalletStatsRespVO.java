package com.stonewu.fusion.controller.wallet.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WalletStatsRespVO {

    private Long quota;

    private Long usedQuota;

    private Long requestCount;

    private Long affQuota;

    private Long affHistoryQuota;

    private Long affCount;

    private Double quotaPerUnit;

    private String quotaDisplayType;

    private String affCode;

    private String inviteUrl;
}
