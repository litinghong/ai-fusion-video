package com.stonewu.fusion.service.storyboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.storyboard.Storyboard;
import com.stonewu.fusion.entity.storyboard.StoryboardEpisode;
import com.stonewu.fusion.entity.storyboard.StoryboardItem;
import com.stonewu.fusion.entity.storyboard.StoryboardScene;
import com.stonewu.fusion.mapper.storyboard.StoryboardEpisodeMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardItemMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardMapper;
import com.stonewu.fusion.mapper.storyboard.StoryboardSceneMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 分镜脚本服务（含分镜集、分镜场次、分镜条目管理）
 */
@Service
@RequiredArgsConstructor
public class StoryboardService {

    private final StoryboardMapper storyboardMapper;
    private final StoryboardEpisodeMapper episodeMapper;
    private final StoryboardSceneMapper sceneMapper;
    private final StoryboardItemMapper itemMapper;

    // ========== 分镜脚本 ==========

    @Cacheable(value = "storyboard", key = "#id")
    public Storyboard getById(Long id) {
        Storyboard sb = storyboardMapper.selectById(id);
        if (sb == null) throw new BusinessException("分镜脚本不存在: " + id);
        return sb;
    }

    public Storyboard getByIdForUser(Long id, Long userId) {
        Storyboard storyboard = getById(id);
        assertStoryboardOwner(storyboard, userId);
        return storyboard;
    }

    @Cacheable(value = "storyboard", key = "'project:' + #projectId")
    public List<Storyboard> listByProject(Long projectId) {
        return storyboardMapper.selectList(new LambdaQueryWrapper<Storyboard>()
                .eq(Storyboard::getProjectId, projectId)
                .orderByDesc(Storyboard::getCreateTime));
    }

    public List<Storyboard> listByProjectForUser(Long projectId, Long userId) {
        return storyboardMapper.selectList(new LambdaQueryWrapper<Storyboard>()
                .eq(Storyboard::getProjectId, projectId)
                .eq(Storyboard::getOwnerId, userId)
                .orderByDesc(Storyboard::getCreateTime));
    }

    @CacheEvict(value = "storyboard", allEntries = true)
    @Transactional
    public Storyboard create(Storyboard storyboard) {
        storyboardMapper.insert(storyboard);
        return storyboard;
    }

    @CacheEvict(value = "storyboard", allEntries = true)
    @Transactional
    public Storyboard createForUser(Storyboard storyboard, Long userId) {
        if (!Objects.equals(storyboard.getOwnerId(), userId)) {
            throw new BusinessException(403, "无权限操作该分镜");
        }
        storyboardMapper.insert(storyboard);
        return storyboard;
    }

    @CacheEvict(value = "storyboard", allEntries = true)
    @Transactional
    public Storyboard update(Storyboard storyboard) {
        getById(storyboard.getId());
        storyboardMapper.updateById(storyboard);
        return storyboardMapper.selectById(storyboard.getId());
    }

    @CacheEvict(value = "storyboard", allEntries = true)
    @Transactional
    public Storyboard updateForUser(Storyboard storyboard, Long userId) {
        Storyboard existing = getById(storyboard.getId());
        assertStoryboardOwner(existing, userId);
        storyboardMapper.updateById(storyboard);
        return storyboardMapper.selectById(storyboard.getId());
    }

    @CacheEvict(value = "storyboard", allEntries = true)
    @Transactional
    public void delete(Long id) {
        storyboardMapper.deleteById(id);
    }

    @CacheEvict(value = "storyboard", allEntries = true)
    @Transactional
    public void deleteForUser(Long id, Long userId) {
        Storyboard existing = getById(id);
        assertStoryboardOwner(existing, userId);
        storyboardMapper.deleteById(id);
    }

    // ========== 分镜集 ==========

    @Cacheable(value = "storyboardEpisode", key = "#id")
    public StoryboardEpisode getEpisodeById(Long id) {
        StoryboardEpisode ep = episodeMapper.selectById(id);
        if (ep == null) throw new BusinessException("分镜集不存在: " + id);
        return ep;
    }

    public StoryboardEpisode getEpisodeByIdForUser(Long id, Long userId) {
        StoryboardEpisode episode = getEpisodeById(id);
        requireStoryboardByIdForUser(episode.getStoryboardId(), userId);
        return episode;
    }

    @Cacheable(value = "storyboardEpisode", key = "'storyboard:' + #storyboardId")
    public List<StoryboardEpisode> listEpisodes(Long storyboardId) {
        return episodeMapper.selectList(new LambdaQueryWrapper<StoryboardEpisode>()
                .eq(StoryboardEpisode::getStoryboardId, storyboardId)
                .orderByAsc(StoryboardEpisode::getSortOrder));
    }

    public List<StoryboardEpisode> listEpisodesForUser(Long storyboardId, Long userId) {
        requireStoryboardByIdForUser(storyboardId, userId);
        return listEpisodes(storyboardId);
    }

    @CacheEvict(value = "storyboardEpisode", allEntries = true)
    @Transactional
    public StoryboardEpisode createEpisode(StoryboardEpisode episode) {
        episodeMapper.insert(episode);
        return episode;
    }

    @CacheEvict(value = "storyboardEpisode", allEntries = true)
    @Transactional
    public StoryboardEpisode createEpisodeForUser(StoryboardEpisode episode, Long userId) {
        requireStoryboardByIdForUser(episode.getStoryboardId(), userId);
        episodeMapper.insert(episode);
        return episode;
    }

    @CacheEvict(value = "storyboardEpisode", allEntries = true)
    @Transactional
    public StoryboardEpisode updateEpisode(StoryboardEpisode episode) {
        getEpisodeById(episode.getId());
        episodeMapper.updateById(episode);
        return episodeMapper.selectById(episode.getId());
    }

    @CacheEvict(value = "storyboardEpisode", allEntries = true)
    @Transactional
    public StoryboardEpisode updateEpisodeForUser(StoryboardEpisode episode, Long userId) {
        StoryboardEpisode existing = getEpisodeById(episode.getId());
        requireStoryboardByIdForUser(existing.getStoryboardId(), userId);
        episodeMapper.updateById(episode);
        return episodeMapper.selectById(episode.getId());
    }

    @CacheEvict(value = "storyboardEpisode", allEntries = true)
    @Transactional
    public void deleteEpisode(Long id) {
        episodeMapper.deleteById(id);
    }

    @CacheEvict(value = "storyboardEpisode", allEntries = true)
    @Transactional
    public void deleteEpisodeForUser(Long id, Long userId) {
        StoryboardEpisode existing = getEpisodeById(id);
        requireStoryboardByIdForUser(existing.getStoryboardId(), userId);
        episodeMapper.deleteById(id);
    }

    // ========== 分镜场次 ==========

    @Cacheable(value = "storyboardScene", key = "#id")
    public StoryboardScene getSceneById(Long id) {
        StoryboardScene scene = sceneMapper.selectById(id);
        if (scene == null) throw new BusinessException("分镜场次不存在: " + id);
        return scene;
    }

    public StoryboardScene getSceneByIdForUser(Long id, Long userId) {
        StoryboardScene scene = getSceneById(id);
        requireStoryboardByIdForUser(scene.getStoryboardId(), userId);
        return scene;
    }

    @Cacheable(value = "storyboardScene", key = "'episode:' + #episodeId")
    public List<StoryboardScene> listScenesByEpisode(Long episodeId) {
        return sceneMapper.selectList(new LambdaQueryWrapper<StoryboardScene>()
                .eq(StoryboardScene::getEpisodeId, episodeId)
                .orderByAsc(StoryboardScene::getSortOrder));
    }

    public List<StoryboardScene> listScenesByEpisodeForUser(Long episodeId, Long userId) {
        StoryboardEpisode episode = getEpisodeById(episodeId);
        requireStoryboardByIdForUser(episode.getStoryboardId(), userId);
        return listScenesByEpisode(episodeId);
    }

    public List<StoryboardScene> listScenesByStoryboard(Long storyboardId) {
        return sceneMapper.selectList(new LambdaQueryWrapper<StoryboardScene>()
                .eq(StoryboardScene::getStoryboardId, storyboardId)
                .orderByAsc(StoryboardScene::getSortOrder));
    }

    public List<StoryboardScene> listScenesByStoryboardForUser(Long storyboardId, Long userId) {
        requireStoryboardByIdForUser(storyboardId, userId);
        return listScenesByStoryboard(storyboardId);
    }

    @CacheEvict(value = "storyboardScene", allEntries = true)
    @Transactional
    public StoryboardScene createScene(StoryboardScene scene) {
        sceneMapper.insert(scene);
        return scene;
    }

    @CacheEvict(value = "storyboardScene", allEntries = true)
    @Transactional
    public StoryboardScene createSceneForUser(StoryboardScene scene, Long userId) {
        StoryboardEpisode episode = getEpisodeById(scene.getEpisodeId());
        Storyboard storyboard = requireStoryboardByIdForUser(episode.getStoryboardId(), userId);
        scene.setStoryboardId(storyboard.getId());
        sceneMapper.insert(scene);
        return scene;
    }

    @CacheEvict(value = "storyboardScene", allEntries = true)
    @Transactional
    public StoryboardScene updateScene(StoryboardScene scene) {
        getSceneById(scene.getId());
        sceneMapper.updateById(scene);
        return sceneMapper.selectById(scene.getId());
    }

    @CacheEvict(value = "storyboardScene", allEntries = true)
    @Transactional
    public StoryboardScene updateSceneForUser(StoryboardScene scene, Long userId) {
        StoryboardScene existing = getSceneById(scene.getId());
        requireStoryboardByIdForUser(existing.getStoryboardId(), userId);
        sceneMapper.updateById(scene);
        return sceneMapper.selectById(scene.getId());
    }

    @CacheEvict(value = "storyboardScene", allEntries = true)
    @Transactional
    public void deleteScene(Long id) {
        sceneMapper.deleteById(id);
    }

    @CacheEvict(value = "storyboardScene", allEntries = true)
    @Transactional
    public void deleteSceneForUser(Long id, Long userId) {
        StoryboardScene existing = getSceneById(id);
        requireStoryboardByIdForUser(existing.getStoryboardId(), userId);
        sceneMapper.deleteById(id);
    }

    // ========== 分镜条目 ==========

    @Cacheable(value = "storyboardItem", key = "#id")
    public StoryboardItem getItemById(Long id) {
        StoryboardItem item = itemMapper.selectById(id);
        if (item == null) throw new BusinessException("分镜条目不存在: " + id);
        return item;
    }

    public StoryboardItem getItemByIdForUser(Long id, Long userId) {
        StoryboardItem item = getItemById(id);
        requireStoryboardByIdForUser(item.getStoryboardId(), userId);
        return item;
    }

    @Cacheable(value = "storyboardItem", key = "'storyboard:' + #storyboardId")
    public List<StoryboardItem> listItems(Long storyboardId) {
        return itemMapper.selectList(new LambdaQueryWrapper<StoryboardItem>()
                .eq(StoryboardItem::getStoryboardId, storyboardId)
                .orderByAsc(StoryboardItem::getSortOrder));
    }

    public List<StoryboardItem> listItemsForUser(Long storyboardId, Long userId) {
        requireStoryboardByIdForUser(storyboardId, userId);
        return listItems(storyboardId);
    }

    public List<StoryboardItem> listItemsByScene(Long sceneId) {
        return itemMapper.selectList(new LambdaQueryWrapper<StoryboardItem>()
                .eq(StoryboardItem::getStoryboardSceneId, sceneId)
                .orderByAsc(StoryboardItem::getSortOrder));
    }

    public List<StoryboardItem> listItemsBySceneForUser(Long sceneId, Long userId) {
        StoryboardScene scene = getSceneById(sceneId);
        requireStoryboardByIdForUser(scene.getStoryboardId(), userId);
        return listItemsByScene(sceneId);
    }

    @CacheEvict(value = "storyboardItem", allEntries = true)
    @Transactional
    public StoryboardItem createItem(StoryboardItem item) {
        itemMapper.insert(item);
        return item;
    }

    @CacheEvict(value = "storyboardItem", allEntries = true)
    @Transactional
    public StoryboardItem createItemForUser(StoryboardItem item, Long userId) {
        requireStoryboardByIdForUser(item.getStoryboardId(), userId);
        itemMapper.insert(item);
        return item;
    }

    @CacheEvict(value = "storyboardItem", allEntries = true)
    @Transactional
    public StoryboardItem updateItem(StoryboardItem item) {
        getItemById(item.getId());
        itemMapper.updateById(item);
        return itemMapper.selectById(item.getId());
    }

    @CacheEvict(value = "storyboardItem", allEntries = true)
    @Transactional
    public StoryboardItem updateItemForUser(StoryboardItem item, Long userId) {
        StoryboardItem existing = getItemById(item.getId());
        requireStoryboardByIdForUser(existing.getStoryboardId(), userId);
        itemMapper.updateById(item);
        return itemMapper.selectById(item.getId());
    }

    @CacheEvict(value = "storyboardItem", allEntries = true)
    @Transactional
    public void deleteItem(Long id) {
        itemMapper.deleteById(id);
    }

    @CacheEvict(value = "storyboardItem", allEntries = true)
    @Transactional
    public void deleteItemForUser(Long id, Long userId) {
        StoryboardItem existing = getItemById(id);
        requireStoryboardByIdForUser(existing.getStoryboardId(), userId);
        itemMapper.deleteById(id);
    }

    @CacheEvict(value = "storyboardItem", allEntries = true)
    @Transactional
    public void batchCreateItems(List<StoryboardItem> items) {
        for (StoryboardItem item : items) {
            itemMapper.insert(item);
        }
    }

    @CacheEvict(value = "storyboardItem", allEntries = true)
    @Transactional
    public void batchCreateItemsForUser(List<StoryboardItem> items, Long userId) {
        for (StoryboardItem item : items) {
            requireStoryboardByIdForUser(item.getStoryboardId(), userId);
            itemMapper.insert(item);
        }
    }

    @CacheEvict(value = "storyboardItem", allEntries = true)
    @Transactional
    public void batchUpdateItemSort(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (int i = 0; i < ids.size(); i++) {
            StoryboardItem item = new StoryboardItem();
            item.setId(ids.get(i));
            item.setSortOrder(i);
            itemMapper.updateById(item);
        }
    }

    @CacheEvict(value = "storyboardItem", allEntries = true)
    @Transactional
    public void batchUpdateItemSortForUser(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            StoryboardItem existing = getItemById(id);
            requireStoryboardByIdForUser(existing.getStoryboardId(), userId);
        }
        for (int i = 0; i < ids.size(); i++) {
            StoryboardItem item = new StoryboardItem();
            item.setId(ids.get(i));
            item.setSortOrder(i);
            itemMapper.updateById(item);
        }
    }

    private Storyboard requireStoryboardByIdForUser(Long storyboardId, Long userId) {
        Storyboard storyboard = getById(storyboardId);
        assertStoryboardOwner(storyboard, userId);
        return storyboard;
    }

    private void assertStoryboardOwner(Storyboard storyboard, Long userId) {
        if (storyboard == null || !Objects.equals(storyboard.getOwnerId(), userId)) {
            throw new BusinessException(404, "分镜不存在");
        }
    }
}
