package com.stonewu.fusion.controller.ai;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.common.PageResult;
import com.stonewu.fusion.controller.ai.vo.ApiConfigPageReqVO;
import com.stonewu.fusion.controller.ai.vo.ApiConfigRespVO;
import com.stonewu.fusion.controller.ai.vo.ApiConfigSaveReqVO;
import com.stonewu.fusion.controller.ai.vo.RemoteModelVO;
import com.stonewu.fusion.convert.ai.ApiConfigConvert;
import com.stonewu.fusion.entity.ai.ApiConfig;
import com.stonewu.fusion.service.ai.ApiConfigService;
import com.stonewu.fusion.service.ai.provider.AiProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.stonewu.fusion.security.SecurityUtils.requireCurrentUserId;
import static com.stonewu.fusion.common.CommonResult.success;

@Tag(name = "API配置管理")
@RestController
@RequestMapping("/ai/api-config")
@RequiredArgsConstructor
public class ApiConfigController {

    private final ApiConfigService apiConfigService;
    private final AiProviderService aiProviderService;

    @PostMapping("/create")
    @Operation(summary = "创建API配置")
    public CommonResult<Long> create(@Valid @RequestBody ApiConfigSaveReqVO reqVO) {
        Long userId = requireCurrentUserId();
        ApiConfig config = ApiConfig.builder()
                .name(reqVO.getName()).platform(reqVO.getPlatform())
                .apiUrl(reqVO.getApiUrl())
                .autoAppendV1Path(reqVO.getAutoAppendV1Path() != null ? reqVO.getAutoAppendV1Path() : true)
                .apiKey(reqVO.getApiKey()).appId(reqVO.getAppId()).appSecret(reqVO.getAppSecret())
                .modelId(reqVO.getModelId()).status(reqVO.getStatus() != null ? reqVO.getStatus() : 1)
                .remark(reqVO.getRemark())
                .build();
        return success(apiConfigService.createApiConfig(config, userId));
    }

    @PutMapping("/update")
    @Operation(summary = "更新API配置")
    public CommonResult<Boolean> update(@Valid @RequestBody ApiConfigSaveReqVO reqVO) {
        Long userId = requireCurrentUserId();
        apiConfigService.updateApiConfig(userId, reqVO.getId(), reqVO.getName(), reqVO.getPlatform(),
                reqVO.getApiUrl(), reqVO.getAutoAppendV1Path(), reqVO.getApiKey(), reqVO.getAppId(),
                reqVO.getAppSecret(), reqVO.getModelId(), reqVO.getStatus(), reqVO.getRemark());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除API配置")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        Long userId = requireCurrentUserId();
        apiConfigService.deleteApiConfig(userId, id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取API配置详情")
    @Parameter(name = "id", description = "配置ID", required = true)
    public CommonResult<ApiConfigRespVO> get(@RequestParam("id") Long id) {
        Long userId = requireCurrentUserId();
        ApiConfig config = apiConfigService.getByIdForUser(id, userId);
        return success(config == null ? null : ApiConfigConvert.INSTANCE.convert(config));
    }

    @GetMapping("/page")
    @Operation(summary = "API配置分页列表")
    public CommonResult<PageResult<ApiConfigRespVO>> page(@Valid ApiConfigPageReqVO reqVO) {
        Long userId = requireCurrentUserId();
        return success(apiConfigService.getPageByUser(userId, reqVO.getName(), reqVO.getPlatform(),
                reqVO.getStatus(), reqVO.getPageNo(), reqVO.getPageSize())
                .map(ApiConfigConvert.INSTANCE::convert));
    }

    @GetMapping("/list")
    @Operation(summary = "获取启用的API配置列表")
    public CommonResult<List<ApiConfigRespVO>> list() {
        Long userId = requireCurrentUserId();
        return success(ApiConfigConvert.INSTANCE.convertList(apiConfigService.getEnabledListByUser(userId)));
    }

    @GetMapping("/remote-models")
    @Operation(summary = "获取远程可用模型列表")
    @Parameter(name = "id", description = "API配置ID", required = true)
    public CommonResult<List<RemoteModelVO>> remoteModels(@RequestParam("id") Long id) {
        Long userId = requireCurrentUserId();
        ApiConfig config = apiConfigService.getByIdForUser(id, userId);
        if (config == null) {
            return CommonResult.error(404, "API配置不存在");
        }
        return success(aiProviderService.listRemoteModels(config));
    }
}
