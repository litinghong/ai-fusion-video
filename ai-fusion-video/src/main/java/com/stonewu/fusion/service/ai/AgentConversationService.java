package com.stonewu.fusion.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.common.BusinessException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stonewu.fusion.common.PageResult;
import com.stonewu.fusion.entity.ai.AgentConversation;
import com.stonewu.fusion.mapper.ai.AgentConversationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Agent 对话索引服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentConversationService {

    private final AgentConversationMapper conversationMapper;

    @Transactional
    public AgentConversation createOrUpdate(String conversationId, Long userId, Long projectId,
                                            String contextType, Long contextId,
                                            String agentType, String title, String category) {
        AgentConversation existing = conversationMapper.selectOne(
                new LambdaQueryWrapper<AgentConversation>().eq(AgentConversation::getConversationId, conversationId));
        if (existing != null) {
            if (title != null) existing.setTitle(title);
            existing.setLastMessageTime(LocalDateTime.now());
            existing.setMessageCount(existing.getMessageCount() + 1);
            conversationMapper.updateById(existing);
            return existing;
        }

        AgentConversation conv = AgentConversation.builder()
                .conversationId(conversationId)
                .userId(userId)
                .projectId(projectId)
                .contextType(contextType)
                .contextId(contextId)
                .agentType(agentType)
                .category(category)
                .title(title != null ? title : "新对话")
                .status("running")
                .messageCount(1)
                .lastMessageTime(LocalDateTime.now())
                .build();
        conversationMapper.insert(conv);
        return conv;
    }

    @Transactional
    public void finish(String conversationId, String status) {
        AgentConversation conv = conversationMapper.selectOne(
                new LambdaQueryWrapper<AgentConversation>().eq(AgentConversation::getConversationId, conversationId));
        if (conv != null) {
            conv.setStatus(status);
            conv.setLastMessageTime(LocalDateTime.now());
            conversationMapper.updateById(conv);
        }
    }

    public AgentConversation getByConversationId(String conversationId) {
        return conversationMapper.selectOne(
                new LambdaQueryWrapper<AgentConversation>().eq(AgentConversation::getConversationId, conversationId));
    }

    public AgentConversation getByConversationIdForUser(String conversationId, Long userId) {
        AgentConversation conversation = getByConversationId(conversationId);
        assertConversationOwner(conversation, userId);
        return conversation;
    }

    public AgentConversation getById(Long id) {
        return conversationMapper.selectById(id);
    }

    public AgentConversation getByIdForUser(Long id, Long userId) {
        AgentConversation conversation = conversationMapper.selectById(id);
        assertConversationOwner(conversation, userId);
        return conversation;
    }

    public PageResult<AgentConversation> listByUser(Long userId, int pageNo, int pageSize) {
        return PageResult.of(conversationMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<AgentConversation>()
                        .eq(AgentConversation::getUserId, userId)
                        .orderByDesc(AgentConversation::getUpdateTime)));
    }

    public PageResult<AgentConversation> listByUserAndCategory(Long userId, String category, int pageNo, int pageSize) {
        return PageResult.of(conversationMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<AgentConversation>()
                        .eq(AgentConversation::getUserId, userId)
                        .eq(AgentConversation::getCategory, category)
                        .orderByDesc(AgentConversation::getUpdateTime)));
    }

    public List<AgentConversation> listByProject(Long projectId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<AgentConversation>()
                .eq(AgentConversation::getProjectId, projectId)
                .orderByDesc(AgentConversation::getUpdateTime));
    }

    /**
     * 查询用户正在运行的 pipeline 对话（status=running）
     */
    public List<AgentConversation> listRunning(Long userId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<AgentConversation>()
                .eq(AgentConversation::getUserId, userId)
                .eq(AgentConversation::getStatus, "running")
                .orderByDesc(AgentConversation::getUpdateTime));
    }

    public void delete(Long id) {
        conversationMapper.deleteById(id);
    }

    public void deleteForUser(Long id, Long userId) {
        AgentConversation conversation = conversationMapper.selectById(id);
        assertConversationOwner(conversation, userId);
        conversationMapper.deleteById(id);
    }

    private void assertConversationOwner(AgentConversation conversation, Long userId) {
        if (conversation == null || !Objects.equals(conversation.getUserId(), userId)) {
            throw new BusinessException(404, "对话不存在");
        }
    }
}
