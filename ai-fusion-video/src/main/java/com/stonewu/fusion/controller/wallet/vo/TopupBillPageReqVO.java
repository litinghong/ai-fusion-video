package com.stonewu.fusion.controller.wallet.vo;

import com.stonewu.fusion.common.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TopupBillPageReqVO extends PageParam {

    private String keyword;
}
