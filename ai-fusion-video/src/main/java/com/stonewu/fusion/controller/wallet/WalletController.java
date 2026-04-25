package com.stonewu.fusion.controller.wallet;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.controller.wallet.vo.AlipayAmountRespVO;
import com.stonewu.fusion.controller.wallet.vo.AlipayPayReqVO;
import com.stonewu.fusion.controller.wallet.vo.AlipayPayRespVO;
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
}
