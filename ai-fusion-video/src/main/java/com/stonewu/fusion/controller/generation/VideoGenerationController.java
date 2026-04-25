package com.stonewu.fusion.controller.generation;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.common.PageParam;
import com.stonewu.fusion.common.PageResult;
import com.stonewu.fusion.controller.generation.vo.VideoTaskSubmitReqVO;
import com.stonewu.fusion.convert.generation.GenerationConvert;
import com.stonewu.fusion.entity.generation.VideoItem;
import com.stonewu.fusion.entity.generation.VideoTask;
import com.stonewu.fusion.service.generation.VideoGenerationService;
import com.stonewu.fusion.service.generation.consumer.VideoGenerationConsumer;
import com.stonewu.fusion.service.project.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.stonewu.fusion.security.SecurityUtils.requireCurrentUserId;

/**
 * 生视频 Controller
 */
@Tag(name = "视频生成")
@RestController
@RequestMapping("/api/generation/video")
@RequiredArgsConstructor
public class VideoGenerationController {

    private final VideoGenerationService videoGenerationService;
    private final VideoGenerationConsumer videoGenerationConsumer;
    private final ProjectService projectService;

    @Operation(summary = "提交生视频任务")
    @PostMapping("/submit")
    public CommonResult<String> submit(@Valid @RequestBody VideoTaskSubmitReqVO reqVO) {
        Long userId = requireCurrentUserId();
        if (reqVO.getProjectId() != null) {
            projectService.getByIdForUser(reqVO.getProjectId(), userId);
        }
        VideoTask task = GenerationConvert.INSTANCE.convert(reqVO);
        task.setUserId(userId);
        String taskId = videoGenerationConsumer.submitTask(task);
        return CommonResult.success(taskId);
    }

    @Operation(summary = "查询生视频任务")
    @GetMapping("/{taskId}")
    public CommonResult<VideoTask> get(@PathVariable String taskId) {
        Long userId = requireCurrentUserId();
        return CommonResult.success(videoGenerationService.getByTaskIdForUser(taskId, userId));
    }

    @Operation(summary = "查询生视频任务的视频条目")
    @GetMapping("/{id}/items")
    public CommonResult<List<VideoItem>> listItems(@PathVariable Long id) {
        Long userId = requireCurrentUserId();
        return CommonResult.success(videoGenerationService.listItemsForUser(id, userId));
    }

    @Operation(summary = "分页查询当前用户的生视频任务")
    @GetMapping("/page")
    public CommonResult<PageResult<VideoTask>> page(PageParam pageParam) {
        Long userId = requireCurrentUserId();
        return CommonResult.success(videoGenerationService.pageByUser(userId,
                pageParam.getPageNo(), pageParam.getPageSize()));
    }
}
