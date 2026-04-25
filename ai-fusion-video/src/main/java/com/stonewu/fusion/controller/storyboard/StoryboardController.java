package com.stonewu.fusion.controller.storyboard;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.controller.storyboard.vo.StoryboardCreateReqVO;
import com.stonewu.fusion.controller.storyboard.vo.StoryboardEpisodeCreateReqVO;
import com.stonewu.fusion.controller.storyboard.vo.StoryboardEpisodeUpdateReqVO;
import com.stonewu.fusion.controller.storyboard.vo.StoryboardItemCreateReqVO;
import com.stonewu.fusion.controller.storyboard.vo.StoryboardItemSortReqVO;
import com.stonewu.fusion.controller.storyboard.vo.StoryboardItemUpdateReqVO;
import com.stonewu.fusion.controller.storyboard.vo.StoryboardSceneCreateReqVO;
import com.stonewu.fusion.controller.storyboard.vo.StoryboardSceneUpdateReqVO;
import com.stonewu.fusion.controller.storyboard.vo.StoryboardUpdateReqVO;
import com.stonewu.fusion.convert.storyboard.StoryboardConvert;
import com.stonewu.fusion.entity.storyboard.Storyboard;
import com.stonewu.fusion.entity.storyboard.StoryboardEpisode;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.entity.storyboard.StoryboardScene;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.storyboard.StoryboardService;
import com.stonewu.fusion.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分镜脚本 Controller
 */
@Tag(name = "分镜管理")
@RestController
@RequestMapping("/api/storyboard")
@RequiredArgsConstructor
public class StoryboardController {

    private final StoryboardService storyboardService;
    private final ProjectService projectService;

    // ========== 分镜脚本 ==========

    @Operation(summary = "获取分镜详情")
    @GetMapping("/{id}")
    public CommonResult<Storyboard> get(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(storyboardService.getByIdForUser(id, userId));
    }

    @Operation(summary = "按项目查询分镜列表")
    @GetMapping("/list")
    public CommonResult<List<Storyboard>> list(@RequestParam Long projectId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(storyboardService.listByProjectForUser(projectId, userId));
    }

    @Operation(summary = "创建分镜")
    @PostMapping
    public CommonResult<Storyboard> create(@Valid @RequestBody StoryboardCreateReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        projectService.getByIdForUser(reqVO.getProjectId(), userId);
        Storyboard storyboard = StoryboardConvert.INSTANCE.convert(reqVO);
        storyboard.setOwnerId(userId);
        storyboard.setOwnerType(1);
        return CommonResult.success(storyboardService.createForUser(storyboard, userId));
    }

    @Operation(summary = "更新分镜")
    @PutMapping
    public CommonResult<Storyboard> update(@Valid @RequestBody StoryboardUpdateReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        Storyboard storyboard = StoryboardConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(storyboardService.updateForUser(storyboard, userId));
    }

    @Operation(summary = "删除分镜")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        storyboardService.deleteForUser(id, userId);
        return CommonResult.success(true);
    }

    // ========== 分镜集 ==========

    @Operation(summary = "获取分镜集列表")
    @GetMapping("/{storyboardId}/episodes")
    public CommonResult<List<StoryboardEpisode>> listEpisodes(@PathVariable Long storyboardId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(storyboardService.listEpisodesForUser(storyboardId, userId));
    }

    @Operation(summary = "获取分镜集详情")
    @GetMapping("/episode/{id}")
    public CommonResult<StoryboardEpisode> getEpisode(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(storyboardService.getEpisodeByIdForUser(id, userId));
    }

    @Operation(summary = "创建分镜集")
    @PostMapping("/episode")
    public CommonResult<StoryboardEpisode> createEpisode(@Valid @RequestBody StoryboardEpisodeCreateReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        StoryboardEpisode episode = StoryboardConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(storyboardService.createEpisodeForUser(episode, userId));
    }

    @Operation(summary = "更新分镜集")
    @PutMapping("/episode")
    public CommonResult<StoryboardEpisode> updateEpisode(@Valid @RequestBody StoryboardEpisodeUpdateReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        StoryboardEpisode episode = StoryboardConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(storyboardService.updateEpisodeForUser(episode, userId));
    }

    @Operation(summary = "删除分镜集")
    @DeleteMapping("/episode/{id}")
    public CommonResult<Boolean> deleteEpisode(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        storyboardService.deleteEpisodeForUser(id, userId);
        return CommonResult.success(true);
    }

    // ========== 分镜场次 ==========

    @Operation(summary = "获取分镜场次列表（按集）")
    @GetMapping("/episode/{episodeId}/scenes")
    public CommonResult<List<StoryboardScene>> listScenesByEpisode(@PathVariable Long episodeId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(storyboardService.listScenesByEpisodeForUser(episodeId, userId));
    }

    @Operation(summary = "获取分镜场次列表（按分镜）")
    @GetMapping("/{storyboardId}/scenes")
    public CommonResult<List<StoryboardScene>> listScenesByStoryboard(@PathVariable Long storyboardId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(storyboardService.listScenesByStoryboardForUser(storyboardId, userId));
    }

    @Operation(summary = "获取分镜场次详情")
    @GetMapping("/scene/{id}")
    public CommonResult<StoryboardScene> getScene(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(storyboardService.getSceneByIdForUser(id, userId));
    }

    @Operation(summary = "创建分镜场次")
    @PostMapping("/scene")
    public CommonResult<StoryboardScene> createScene(@Valid @RequestBody StoryboardSceneCreateReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        StoryboardScene scene = StoryboardConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(storyboardService.createSceneForUser(scene, userId));
    }

    @Operation(summary = "更新分镜场次")
    @PutMapping("/scene")
    public CommonResult<StoryboardScene> updateScene(@Valid @RequestBody StoryboardSceneUpdateReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        StoryboardScene scene = StoryboardConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(storyboardService.updateSceneForUser(scene, userId));
    }

    @Operation(summary = "删除分镜场次")
    @DeleteMapping("/scene/{id}")
    public CommonResult<Boolean> deleteScene(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        storyboardService.deleteSceneForUser(id, userId);
        return CommonResult.success(true);
    }

    // ========== 分镜条目 ==========

    @Operation(summary = "获取分镜条目列表（按分镜）")
    @GetMapping("/{storyboardId}/items")
    public CommonResult<List<StoryboardItem>> listItems(@PathVariable Long storyboardId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(storyboardService.listItemsForUser(storyboardId, userId));
    }

    @Operation(summary = "获取分镜条目列表（按场次）")
    @GetMapping("/scene/{sceneId}/items")
    public CommonResult<List<StoryboardItem>> listItemsByScene(@PathVariable Long sceneId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(storyboardService.listItemsBySceneForUser(sceneId, userId));
    }

    @Operation(summary = "获取分镜条目详情")
    @GetMapping("/item/{id}")
    public CommonResult<StoryboardItem> getItem(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(storyboardService.getItemByIdForUser(id, userId));
    }

    @Operation(summary = "创建分镜条目")
    @PostMapping("/item")
    public CommonResult<StoryboardItem> createItem(@Valid @RequestBody StoryboardItemCreateReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        StoryboardItem item = StoryboardConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(storyboardService.createItemForUser(item, userId));
    }

    @Operation(summary = "更新分镜条目")
    @PutMapping("/item")
    public CommonResult<StoryboardItem> updateItem(@Valid @RequestBody StoryboardItemUpdateReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        StoryboardItem item = StoryboardConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(storyboardService.updateItemForUser(item, userId));
    }

    @Operation(summary = "删除分镜条目")
    @DeleteMapping("/item/{id}")
    public CommonResult<Boolean> deleteItem(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        storyboardService.deleteItemForUser(id, userId);
        return CommonResult.success(true);
    }

    @Operation(summary = "批量创建分镜条目")
    @PostMapping("/{storyboardId}/items/batch")
    public CommonResult<Boolean> batchCreate(@PathVariable Long storyboardId,
                                             @RequestBody List<StoryboardItemCreateReqVO> reqVOList) {
        Long userId = SecurityUtils.requireCurrentUserId();
        List<StoryboardItem> items = StoryboardConvert.INSTANCE.convertCreateList(reqVOList);
        items.forEach(item -> item.setStoryboardId(storyboardId));
        storyboardService.batchCreateItemsForUser(items, userId);
        return CommonResult.success(true);
    }

    @Operation(summary = "批量更新分镜条目排序")
    @PostMapping("/items/batch-sort")
    public CommonResult<Boolean> batchUpdateSort(@Valid @RequestBody StoryboardItemSortReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        storyboardService.batchUpdateItemSortForUser(reqVO.getIds(), userId);
        return CommonResult.success(true);
    }
}
