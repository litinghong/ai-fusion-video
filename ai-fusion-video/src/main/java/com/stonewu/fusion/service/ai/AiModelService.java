package com.stonewu.fusion.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.common.PageResult;
import com.stonewu.fusion.entity.ai.AiModel;
import com.stonewu.fusion.mapper.ai.AiModelMapper;
import com.stonewu.fusion.service.ai.agentscope.AgentScopeModelFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiModelMapper aiModelMapper;
    private final ApiConfigService apiConfigService;
    private final ModelPresetService modelPresetService;
    private final ChatModelFactory chatModelFactory;
    private final AgentScopeModelFactory agentScopeModelFactory;

    @Transactional
    public Long createAiModel(AiModel aiModel, Long userId) {
        if (userId == null) {
            throw new BusinessException(400, "缺少用户上下文");
        }
        aiModel.setUserId(userId);
        validateApiConfigForUser(aiModel.getApiConfigId(), userId, true);
        validateUniqueCode(userId, null, aiModel.getApiConfigId(), aiModel.getCode());
        // 如果用户未设置 config，尝试从预设自动填充
        if (StrUtil.isBlank(aiModel.getConfig()) && StrUtil.isNotBlank(aiModel.getCode())) {
            String presetConfig = modelPresetService.getPresetConfig(aiModel.getCode());
            if (presetConfig != null) {
                aiModel.setConfig(presetConfig);
            }
        }
        try {
            aiModelMapper.insert(aiModel);
        } catch (DuplicateKeyException e) {
            throwDuplicateCodeException(aiModel.getApiConfigId());
        }
        return aiModel.getId();
    }

    @Transactional
    public void updateAiModel(Long userId, Long id, String name, String code, Integer modelType,
                              String icon, String description, Integer sort,
                              Integer status, String config, Boolean defaultModel,
                              Long apiConfigId, Integer maxConcurrency,
                              Boolean supportVision, Boolean supportReasoning,
                              Integer contextWindow) {
        AiModel model = getByIdForUser(id, userId);
        if (model == null) {
            throw new BusinessException(404, "AI模型不存在");
        }
        Long nextApiConfigId = apiConfigId != null ? apiConfigId : model.getApiConfigId();
        String nextCode = code != null ? code : model.getCode();
        validateApiConfigForUser(apiConfigId, userId, false);
        validateUniqueCode(userId, id, nextApiConfigId, nextCode);
        if (name != null) model.setName(name);
        if (code != null) model.setCode(code);
        if (modelType != null) model.setModelType(modelType);
        if (icon != null) model.setIcon(icon);
        if (description != null) model.setDescription(description);
        if (sort != null) model.setSort(sort);
        if (status != null) model.setStatus(status);
        if (config != null) model.setConfig(config);
        if (maxConcurrency != null) model.setMaxConcurrency(maxConcurrency > 0 ? maxConcurrency : 5);
        if (defaultModel != null) model.setDefaultModel(defaultModel);
        if (supportVision != null) model.setSupportVision(supportVision);
        if (supportReasoning != null) model.setSupportReasoning(supportReasoning);
        if (contextWindow != null) model.setContextWindow(contextWindow > 0 ? contextWindow : null);
        if (apiConfigId != null) model.setApiConfigId(apiConfigId);
        try {
            aiModelMapper.updateById(model);
        } catch (DuplicateKeyException e) {
            throwDuplicateCodeException(nextApiConfigId);
        }
        chatModelFactory.evict(id);
        agentScopeModelFactory.evict(id);
    }

    @Transactional
    public void deleteAiModel(Long userId, Long id) {
        AiModel model = getByIdForUser(id, userId);
        if (model == null) {
            throw new BusinessException(404, "AI模型不存在");
        }
        aiModelMapper.softDeleteById(id);
        chatModelFactory.evict(id);
        agentScopeModelFactory.evict(id);
    }

    public AiModel getById(Long id) {
        return aiModelMapper.selectById(id);
    }

    public AiModel getByIdForUser(Long id, Long userId) {
        if (id == null || userId == null) {
            return null;
        }
        return aiModelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getId, id)
                .eq(AiModel::getUserId, userId)
                .last("LIMIT 1"));
    }

    public PageResult<AiModel> getPageByUser(Long userId, String name, String code, Integer modelType,
                                             Integer status, int pageNo, int pageSize) {
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModel::getUserId, userId)
                .like(name != null, AiModel::getName, name)
                .like(code != null, AiModel::getCode, code)
                .eq(modelType != null, AiModel::getModelType, modelType)
                .eq(status != null, AiModel::getStatus, status)
                .orderByAsc(AiModel::getSort)
                .orderByDesc(AiModel::getId);
        return PageResult.of(aiModelMapper.selectPage(new Page<>(pageNo, pageSize), wrapper));
    }

    public List<AiModel> getEnabledListByUser(Long userId) {
        return aiModelMapper.selectList(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getUserId, userId)
                .eq(AiModel::getStatus, 1));
    }

    public List<AiModel> getListByTypeForUser(Long userId, Integer modelType) {
        return aiModelMapper.selectList(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getUserId, userId)
                .eq(AiModel::getStatus, 1)
                .eq(AiModel::getModelType, modelType));
    }

    public AiModel getDefaultByTypeForUser(Long userId, Integer modelType) {
        return aiModelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getUserId, userId)
                .eq(AiModel::getDefaultModel, true)
                .eq(AiModel::getModelType, modelType)
                .eq(AiModel::getStatus, 1)
                .orderByAsc(AiModel::getSort)
                .last("LIMIT 1"));
    }

    @Transactional
    public void upsertModelByCodeForUser(Long userId, Long apiConfigId, String modelCode, Integer modelType) {
        if (userId == null || apiConfigId == null || StrUtil.isBlank(modelCode)) {
            return;
        }
        Integer resolvedModelType = modelType != null ? modelType : 1;
        AiModel existing = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getUserId, userId)
                .eq(AiModel::getApiConfigId, apiConfigId)
                .eq(AiModel::getCode, modelCode)
                .last("LIMIT 1"));
        if (existing != null) {
            boolean changed = false;
            if (existing.getStatus() == null || existing.getStatus() != 1) {
                existing.setStatus(1);
                changed = true;
            }
            if (existing.getModelType() == null) {
                existing.setModelType(resolvedModelType);
                changed = true;
            }
            if (changed) {
                aiModelMapper.updateById(existing);
                chatModelFactory.evict(existing.getId());
                agentScopeModelFactory.evict(existing.getId());
            }
            return;
        }

        createAiModel(AiModel.builder()
                .userId(userId)
                .name(modelCode)
                .code(modelCode)
                .modelType(resolvedModelType)
                .status(1)
                .apiConfigId(apiConfigId)
                .build(), userId);
    }

    private void validateApiConfigForUser(Long apiConfigId, Long userId, boolean required) {
        if (apiConfigId == null) {
            if (required) {
                throw new BusinessException(400, "请选择 API 配置");
            }
            return;
        }
        if (apiConfigService.getByIdForUser(apiConfigId, userId) == null) {
            throw new BusinessException(404, "API 配置不存在");
        }
    }

    private void validateUniqueCode(Long userId, Long currentId, Long apiConfigId, String code) {
        if (StrUtil.isBlank(code)) {
            return;
        }
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getUserId, userId)
                .eq(AiModel::getCode, code);
        if (apiConfigId != null) {
            wrapper.eq(AiModel::getApiConfigId, apiConfigId);
        } else {
            wrapper.isNull(AiModel::getApiConfigId);
        }
        if (currentId != null) {
            wrapper.ne(AiModel::getId, currentId);
        }
        if (aiModelMapper.exists(wrapper)) {
            throw new BusinessException(400,
                    apiConfigId != null ? "同一 API 配置下模型标识已存在" : "未绑定 API 配置的模型标识已存在");
        }
    }

    private void throwDuplicateCodeException(Long apiConfigId) {
        throw new BusinessException(400,
                apiConfigId != null ? "同一 API 配置下模型标识已存在" : "未绑定 API 配置的模型标识已存在");
    }

    // ========= 兼容旧调用（无用户隔离场景） =========

    public PageResult<AiModel> getPage(String name, String code, Integer modelType, Integer status,
                                       int pageNo, int pageSize) {
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(name != null, AiModel::getName, name)
                .like(code != null, AiModel::getCode, code)
                .eq(modelType != null, AiModel::getModelType, modelType)
                .eq(status != null, AiModel::getStatus, status)
                .orderByAsc(AiModel::getSort)
                .orderByDesc(AiModel::getId);
        return PageResult.of(aiModelMapper.selectPage(new Page<>(pageNo, pageSize), wrapper));
    }

    public List<AiModel> getEnabledList() {
        return aiModelMapper.selectList(new LambdaQueryWrapper<AiModel>().eq(AiModel::getStatus, 1));
    }

    public List<AiModel> getListByType(Integer modelType) {
        return aiModelMapper.selectList(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getStatus, 1)
                .eq(AiModel::getModelType, modelType));
    }

    public AiModel getDefaultByType(Integer modelType) {
        return aiModelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getDefaultModel, true)
                .eq(AiModel::getModelType, modelType)
                .eq(AiModel::getStatus, 1)
                .orderByAsc(AiModel::getSort)
                .last("LIMIT 1"));
    }
}
