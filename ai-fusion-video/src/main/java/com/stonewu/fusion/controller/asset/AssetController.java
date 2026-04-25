package com.stonewu.fusion.controller.asset;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.controller.asset.vo.AssetCreateReqVO;
import com.stonewu.fusion.controller.asset.vo.AssetItemCreateReqVO;
import com.stonewu.fusion.controller.asset.vo.AssetItemUpdateReqVO;
import com.stonewu.fusion.controller.asset.vo.AssetUpdateReqVO;
import com.stonewu.fusion.convert.asset.AssetConvert;
import com.stonewu.fusion.entity.asset.Asset;
import com.stonewu.fusion.entity.asset.AssetItem;
import com.stonewu.fusion.service.asset.AssetService;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.stonewu.fusion.service.asset.AssetMetadataRegistry;
import com.stonewu.fusion.service.asset.AssetMetadataRegistry.FieldDef;

import java.util.List;
import java.util.Map;

/**
 * 资产管理 Controller
 */
@Tag(name = "资产管理")
@RestController
@RequestMapping("/api/asset")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;
    private final ProjectService projectService;

    // ========== 元数据 ==========

    @Operation(summary = "查询资产属性字段定义")
    @GetMapping("/metadata/{assetType}")
    public CommonResult<Map<String, Object>> getMetadata(@PathVariable String assetType) {
        List<FieldDef> fields = AssetMetadataRegistry.getFields(assetType);
        if (fields == null) {
            return CommonResult.error(400, "不支持的资产类型: " + assetType);
        }
        return CommonResult.success(Map.of(
                "assetType", assetType,
                "fields", fields
        ));
    }

    // ========== 资产 ==========

    @Operation(summary = "获取资产详情")
    @GetMapping("/{id}")
    public CommonResult<Asset> get(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(assetService.getByIdForUser(id, userId));
    }

    @Operation(summary = "按项目+类型查询资产列表")
    @GetMapping("/list")
    public CommonResult<List<Asset>> list(@RequestParam Long projectId,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) String keyword) {
        Long userId = SecurityUtils.requireCurrentUserId();
        projectService.getByIdForUser(projectId, userId);
        return CommonResult.success(assetService.listByProjectForUser(projectId, type, keyword, userId));
    }

    @Operation(summary = "按项目查询资产及其所有子资产")
    @GetMapping("/list-with-items")
    public CommonResult<List<Map<String, Object>>> listWithItems(@RequestParam Long projectId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        projectService.getByIdForUser(projectId, userId);
        return CommonResult.success(assetService.listWithItemsByProjectForUser(projectId, userId));
    }

    @Operation(summary = "分页查询当前用户的资产（跨项目），含类型统计")
    @GetMapping("/all")
    public CommonResult<Map<String, Object>> listAll(@RequestParam(required = false) Long projectId,
                                                      @RequestParam(required = false) String type,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtils.requireCurrentUserId();
        IPage<Asset> pageResult = assetService.pageByUser(userId, projectId, type, keyword, page, size);
        // 统计各类型数量：始终反映用户全局数据，不受筛选条件影响
        Map<String, Long> typeCounts = assetService.countByUserGroupByType(userId, null, null);
        return CommonResult.success(Map.of(
                "records", pageResult.getRecords(),
                "total", pageResult.getTotal(),
                "page", pageResult.getCurrent(),
                "size", pageResult.getSize(),
                "typeCounts", typeCounts
        ));
    }

    @Operation(summary = "创建资产")
    @PostMapping
    public CommonResult<Asset> create(@Valid @RequestBody AssetCreateReqVO reqVO) {
        Asset asset = AssetConvert.INSTANCE.convert(reqVO);
        // userId / ownerType / ownerId 由后端决定
        Long userId = SecurityUtils.requireCurrentUserId();
        projectService.getByIdForUser(reqVO.getProjectId(), userId);
        asset.setUserId(userId);
        asset.setOwnerId(userId);
        asset.setOwnerType(1);
        return CommonResult.success(assetService.create(asset));
    }

    @Operation(summary = "更新资产")
    @PutMapping
    public CommonResult<Asset> update(@Valid @RequestBody AssetUpdateReqVO reqVO) {
        Asset asset = AssetConvert.INSTANCE.convert(reqVO);
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(assetService.updateForUser(asset, userId));
    }

    @Operation(summary = "删除资产")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        assetService.deleteForUser(id, userId);
        return CommonResult.success(true);
    }

    // ========== 子资产 ==========

    @Operation(summary = "获取子资产详情")
    @GetMapping("/item/{id}")
    public CommonResult<AssetItem> getItem(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(assetService.getItemByIdForUser(id, userId));
    }

    @Operation(summary = "获取子资产列表")
    @GetMapping("/{assetId}/items")
    public CommonResult<List<AssetItem>> listItems(@PathVariable Long assetId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(assetService.listItemsForUser(assetId, userId));
    }

    @Operation(summary = "创建子资产")
    @PostMapping("/item")
    public CommonResult<AssetItem> createItem(@Valid @RequestBody AssetItemCreateReqVO reqVO) {
        AssetItem item = AssetConvert.INSTANCE.convert(reqVO);
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(assetService.createItemForUser(item, userId));
    }

    @Operation(summary = "更新子资产")
    @PutMapping("/item")
    public CommonResult<AssetItem> updateItem(@Valid @RequestBody AssetItemUpdateReqVO reqVO) {
        AssetItem item = AssetConvert.INSTANCE.convert(reqVO);
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(assetService.updateItemForUser(item, userId));
    }

    @Operation(summary = "删除子资产")
    @DeleteMapping("/item/{id}")
    public CommonResult<Boolean> deleteItem(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        assetService.deleteItemForUser(id, userId);
        return CommonResult.success(true);
    }
}
