package com.stonewu.fusion.controller.wallet;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.common.PageResult;
import com.stonewu.fusion.controller.wallet.vo.AlipayAmountRespVO;
import com.stonewu.fusion.controller.wallet.vo.AlipayPayReqVO;
import com.stonewu.fusion.controller.wallet.vo.AlipayPayRespVO;
import com.stonewu.fusion.controller.wallet.vo.TopupBillPageReqVO;
import com.stonewu.fusion.controller.wallet.vo.TopupBillRespVO;
import com.stonewu.fusion.controller.wallet.vo.TopupReqVO;
import com.stonewu.fusion.controller.wallet.vo.TopupRespVO;
import com.stonewu.fusion.controller.wallet.vo.WalletCapabilitiesRespVO;
import com.stonewu.fusion.controller.wallet.vo.WalletStatsRespVO;
import com.stonewu.fusion.service.wallet.NewApiWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static com.stonewu.fusion.common.CommonResult.success;
import static com.stonewu.fusion.security.SecurityUtils.requireCurrentUserId;

@Tag(name = "钱包管理")
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final NewApiWalletService newApiWalletService;

    @GetMapping("/capabilities")
    @Operation(summary = "获取钱包功能开关")
    public CommonResult<WalletCapabilitiesRespVO> getCapabilities() {
        return success(newApiWalletService.getCapabilities());
    }

    @GetMapping("/stats")
    @Operation(summary = "获取账户统计")
    public CommonResult<WalletStatsRespVO> getStats() {
        return success(newApiWalletService.getStats(requireCurrentUserId()));
    }

    @GetMapping("/alipay/amounts")
    @Operation(summary = "获取支付宝参考充值金额")
    public CommonResult<AlipayAmountRespVO> getAlipayAmounts() {
        return success(newApiWalletService.getAlipayAmounts(requireCurrentUserId()));
    }

    @PostMapping("/alipay/pay")
    @Operation(summary = "创建支付宝充值链接")
    public CommonResult<AlipayPayRespVO> createAlipayPayUrl(@Valid @RequestBody AlipayPayReqVO reqVO) {
        return success(newApiWalletService.createAlipayPayUrl(
                requireCurrentUserId(),
                reqVO.getAmount(),
                reqVO.getProductId()));
    }

    @GetMapping("/topup/bills")
    @Operation(summary = "获取充值账单")
    public CommonResult<PageResult<TopupBillRespVO>> getTopupBills(@Valid TopupBillPageReqVO reqVO) {
        return success(newApiWalletService.getTopupBills(requireCurrentUserId(), reqVO));
    }

    @PostMapping("/topup")
    @Operation(summary = "兑换码充值")
    public CommonResult<TopupRespVO> topup(@Valid @RequestBody TopupReqVO reqVO) {
        return success(newApiWalletService.topup(requireCurrentUserId(), reqVO.getKey()));
    }
}
