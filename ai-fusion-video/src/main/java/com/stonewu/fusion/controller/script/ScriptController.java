package com.stonewu.fusion.controller.script;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.controller.script.vo.EpisodeCreateReqVO;
import com.stonewu.fusion.controller.script.vo.EpisodeUpdateReqVO;
import com.stonewu.fusion.controller.script.vo.SceneCreateReqVO;
import com.stonewu.fusion.controller.script.vo.SceneUpdateReqVO;
import com.stonewu.fusion.controller.script.vo.ScriptCreateReqVO;
import com.stonewu.fusion.controller.script.vo.ScriptUpdateReqVO;
import com.stonewu.fusion.convert.script.ScriptConvert;
import com.stonewu.fusion.entity.script.ScriptSceneItem;
import com.stonewu.fusion.entity.script.Script;
import com.stonewu.fusion.entity.script.ScriptEpisode;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.script.ScriptService;
import com.stonewu.fusion.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 剧本 Controller（含分集、分场次）
 */
@Tag(name = "剧本管理")
@RestController
@RequestMapping("/api/script")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;
    private final ProjectService projectService;

    // ========== 剧本 ==========

    @Operation(summary = "获取剧本详情")
    @GetMapping("/{id}")
    public CommonResult<Script> get(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(scriptService.getByIdForUser(id, userId));
    }

    @Operation(summary = "按项目查询剧本列表")
    @GetMapping("/list")
    public CommonResult<List<Script>> list(@RequestParam Long projectId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(scriptService.listByProjectForUser(projectId, userId));
    }

    @Operation(summary = "创建剧本")
    @PostMapping
    public CommonResult<Script> create(@Valid @RequestBody ScriptCreateReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        projectService.getByIdForUser(reqVO.getProjectId(), userId);
        Script script = ScriptConvert.INSTANCE.convert(reqVO);
        // ownerId 和 ownerType 由后端决定，不信任前端传值，当前固定个人版
        script.setOwnerId(userId);
        script.setOwnerType(1);
        return CommonResult.success(scriptService.createForUser(script, userId));
    }

    @Operation(summary = "更新剧本")
    @PutMapping
    public CommonResult<Script> update(@Valid @RequestBody ScriptUpdateReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        Script script = ScriptConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(scriptService.updateForUser(script, userId));
    }

    @Operation(summary = "删除剧本")
    @DeleteMapping("/{id}")
    public CommonResult<Boolean> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        scriptService.deleteForUser(id, userId);
        return CommonResult.success(true);
    }

    // ========== 分集 ==========

    @Operation(summary = "获取分集列表")
    @GetMapping("/{scriptId}/episodes")
    public CommonResult<List<ScriptEpisode>> listEpisodes(@PathVariable Long scriptId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(scriptService.listEpisodesForUser(scriptId, userId));
    }

    @Operation(summary = "获取分集详情")
    @GetMapping("/episode/{id}")
    public CommonResult<ScriptEpisode> getEpisode(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(scriptService.getEpisodeByIdForUser(id, userId));
    }

    @Operation(summary = "创建分集")
    @PostMapping("/episode")
    public CommonResult<ScriptEpisode> createEpisode(@Valid @RequestBody EpisodeCreateReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        ScriptEpisode episode = ScriptConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(scriptService.createEpisodeForUser(episode, userId));
    }

    @Operation(summary = "更新分集")
    @PutMapping("/episode")
    public CommonResult<ScriptEpisode> updateEpisode(@Valid @RequestBody EpisodeUpdateReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        ScriptEpisode episode = ScriptConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(scriptService.updateEpisodeForUser(episode, userId));
    }

    @Operation(summary = "删除分集")
    @DeleteMapping("/episode/{id}")
    public CommonResult<Boolean> deleteEpisode(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        scriptService.deleteEpisodeForUser(id, userId);
        return CommonResult.success(true);
    }

    // ========== 分场次 ==========

    @Operation(summary = "获取分场次列表（按分集）")
    @GetMapping("/episode/{episodeId}/scenes")
    public CommonResult<List<ScriptSceneItem>> listScenes(@PathVariable Long episodeId) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(scriptService.listScenesByEpisodeForUser(episodeId, userId));
    }

    @Operation(summary = "获取分场次详情")
    @GetMapping("/scene/{id}")
    public CommonResult<ScriptSceneItem> getScene(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        return CommonResult.success(scriptService.getSceneByIdForUser(id, userId));
    }

    @Operation(summary = "创建分场次")
    @PostMapping("/scene")
    public CommonResult<ScriptSceneItem> createScene(@Valid @RequestBody SceneCreateReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        ScriptSceneItem scene = ScriptConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(scriptService.createSceneForUser(scene, userId));
    }

    @Operation(summary = "更新分场次")
    @PutMapping("/scene")
    public CommonResult<ScriptSceneItem> updateScene(@Valid @RequestBody SceneUpdateReqVO reqVO) {
        Long userId = SecurityUtils.requireCurrentUserId();
        ScriptSceneItem scene = ScriptConvert.INSTANCE.convert(reqVO);
        return CommonResult.success(scriptService.updateSceneForUser(scene, userId));
    }

    @Operation(summary = "删除分场次")
    @DeleteMapping("/scene/{id}")
    public CommonResult<Boolean> deleteScene(@PathVariable Long id) {
        Long userId = SecurityUtils.requireCurrentUserId();
        scriptService.deleteSceneForUser(id, userId);
        return CommonResult.success(true);
    }
}
