package com.stonewu.fusion.controller.storage;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.controller.storage.vo.StorageConfigRespVO;
import com.stonewu.fusion.controller.storage.vo.StorageConfigSaveReqVO;
import com.stonewu.fusion.convert.storage.StorageConfigConvert;
import com.stonewu.fusion.entity.storage.StorageConfig;
import com.stonewu.fusion.service.storage.StorageConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.stonewu.fusion.common.CommonResult.success;

/**
 * 存储配置管理
 */
@Tag(name = "存储配置管理")
@RestController
@RequestMapping("/storage/config")
@RequiredArgsConstructor
public class StorageConfigController {

    private final StorageConfigService storageConfigService;

    @PostMapping("/create")
    @Operation(summary = "创建存储配置")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<Long> create(@Valid @RequestBody StorageConfigSaveReqVO reqVO) {
        StorageConfig config = buildConfig(reqVO);
        return success(storageConfigService.create(config));
    }

    @PutMapping("/update")
    @Operation(summary = "更新存储配置")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<Boolean> update(@Valid @RequestBody StorageConfigSaveReqVO reqVO) {
        StorageConfig config = storageConfigService.getById(reqVO.getId());
        if (reqVO.getName() != null) config.setName(reqVO.getName());
        if (reqVO.getType() != null) config.setType(reqVO.getType());
        if (reqVO.getEndpoint() != null) config.setEndpoint(reqVO.getEndpoint());
        if (reqVO.getBucketName() != null) config.setBucketName(reqVO.getBucketName());
        if (reqVO.getAccessKey() != null) config.setAccessKey(reqVO.getAccessKey());
        if (reqVO.getSecretKey() != null) config.setSecretKey(reqVO.getSecretKey());
        if (reqVO.getRegion() != null) config.setRegion(reqVO.getRegion());
        if (reqVO.getBasePath() != null) config.setBasePath(reqVO.getBasePath());
        if (reqVO.getCustomDomain() != null) config.setCustomDomain(reqVO.getCustomDomain());
        if (reqVO.getIsDefault() != null) config.setIsDefault(reqVO.getIsDefault());
        if (reqVO.getStatus() != null) config.setStatus(reqVO.getStatus());
        if (reqVO.getRemark() != null) config.setRemark(reqVO.getRemark());
        storageConfigService.update(config);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除存储配置")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        storageConfigService.delete(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取存储配置详情")
    @Parameter(name = "id", description = "配置ID", required = true)
    public CommonResult<StorageConfigRespVO> get(@RequestParam("id") Long id) {
        StorageConfig config = storageConfigService.getById(id);
        return success(StorageConfigConvert.INSTANCE.convert(config));
    }

    @GetMapping("/list")
    @Operation(summary = "获取启用的存储配置列表")
    public CommonResult<List<StorageConfigRespVO>> list() {
        return success(StorageConfigConvert.INSTANCE.convertList(storageConfigService.getEnabledList()));
    }

    @PutMapping("/set-default")
    @Operation(summary = "设置默认存储配置")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<Boolean> setDefault(@RequestParam("id") Long id) {
        storageConfigService.setDefault(id);
        return success(true);
    }

    @PostMapping("/test")
    @Operation(summary = "测试存储配置读写权限")
    @PreAuthorize("hasRole('ADMIN')")
    public CommonResult<String> test(@Valid @RequestBody StorageConfigSaveReqVO reqVO) {
        StorageConfig config = buildConfig(reqVO);
        storageConfigService.testReadWrite(config);
        return success("存储读写权限测试通过");
    }

    private StorageConfig buildConfig(StorageConfigSaveReqVO reqVO) {
        return StorageConfig.builder()
                .id(reqVO.getId())
                .name(reqVO.getName())
                .type(reqVO.getType())
                .endpoint(reqVO.getEndpoint())
                .bucketName(reqVO.getBucketName())
                .accessKey(reqVO.getAccessKey())
                .secretKey(reqVO.getSecretKey())
                .region(reqVO.getRegion())
                .basePath(reqVO.getBasePath())
                .customDomain(reqVO.getCustomDomain())
                .isDefault(reqVO.getIsDefault() != null ? reqVO.getIsDefault() : false)
                .status(reqVO.getStatus() != null ? reqVO.getStatus() : 1)
                .remark(reqVO.getRemark())
                .build();
    }
}
