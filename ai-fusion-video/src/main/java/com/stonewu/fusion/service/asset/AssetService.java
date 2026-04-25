package com.stonewu.fusion.service.asset;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.asset.Asset;
import com.stonewu.fusion.entity.asset.AssetItem;
import com.stonewu.fusion.mapper.asset.AssetItemMapper;
import com.stonewu.fusion.mapper.asset.AssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 资产服务
 */
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetMapper assetMapper;
    private final AssetItemMapper assetItemMapper;

    // ========== 资产 ==========

    @Cacheable(value = "asset", key = "#id")
    public Asset getById(Long id) {
        Asset asset = assetMapper.selectById(id);
        if (asset == null)
            throw new BusinessException("资产不存在: " + id);
        return asset;
    }

    public Asset getByIdForUser(Long id, Long userId) {
        Asset asset = getById(id);
        assertAssetOwner(asset, userId);
        return asset;
    }

    public List<Asset> listByProject(Long projectId) {
        return assetMapper.selectList(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getProjectId, projectId)
                .orderByDesc(Asset::getCreateTime));
    }

    public List<Asset> listByProject(Long projectId, String type, String keyword) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .eq(Asset::getProjectId, projectId)
                .orderByDesc(Asset::getCreateTime);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Asset::getType, type);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Asset::getName, keyword.trim());
        }
        return assetMapper.selectList(wrapper);
    }

    public List<Asset> listByProjectForUser(Long projectId, String type, String keyword, Long userId) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .eq(Asset::getProjectId, projectId)
                .eq(Asset::getUserId, userId)
                .orderByDesc(Asset::getCreateTime);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Asset::getType, type);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Asset::getName, keyword.trim());
        }
        return assetMapper.selectList(wrapper);
    }

    public List<Map<String, Object>> listWithItemsByProject(Long projectId) {
        List<Asset> assets = listByProject(projectId);
        if (assets.isEmpty())
            return List.of();

        List<Long> assetIds = assets.stream().map(Asset::getId).collect(Collectors.toList());
        List<AssetItem> allItems = assetItemMapper.selectList(new LambdaQueryWrapper<AssetItem>()
                .in(AssetItem::getAssetId, assetIds)
                .orderByAsc(AssetItem::getSortOrder));

        Map<Long, List<AssetItem>> itemsMap = allItems.stream()
                .collect(Collectors.groupingBy(AssetItem::getAssetId));

        return assets.stream().map(asset -> {
            Map<String, Object> map = BeanUtil.beanToMap(asset, false, true);
            map.put("items", itemsMap.getOrDefault(asset.getId(), List.of()));
            return map;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> listWithItemsByProjectForUser(Long projectId, Long userId) {
        List<Asset> assets = listByProjectForUser(projectId, null, null, userId);
        if (assets.isEmpty()) {
            return List.of();
        }
        List<Long> assetIds = assets.stream().map(Asset::getId).toList();
        List<AssetItem> allItems = assetItemMapper.selectList(new LambdaQueryWrapper<AssetItem>()
                .in(AssetItem::getAssetId, assetIds)
                .orderByAsc(AssetItem::getSortOrder));

        Map<Long, List<AssetItem>> itemsMap = allItems.stream()
                .collect(Collectors.groupingBy(AssetItem::getAssetId));
        return assets.stream().map(asset -> {
            Map<String, Object> map = BeanUtil.beanToMap(asset, false, true);
            map.put("items", itemsMap.getOrDefault(asset.getId(), List.of()));
            return map;
        }).toList();
    }

    /**
     * 按用户分页查询资产（跨项目），支持可选的 projectId / type / keyword 过滤
     */
    public IPage<Asset> pageByUser(Long userId, Long projectId, String type, String keyword, int page, int size) {
        LambdaQueryWrapper<Asset> wrapper = buildUserQueryWrapper(userId, projectId, type, keyword);
        return assetMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 统计当前用户各类型资产数量（按 projectId / keyword 过滤，不按 type 过滤）
     * 返回 Map: type -> count
     */
    public Map<String, Long> countByUserGroupByType(Long userId, Long projectId, String keyword) {
        LambdaQueryWrapper<Asset> wrapper = buildUserQueryWrapper(userId, projectId, null, keyword);
        List<Asset> all = assetMapper.selectList(
                wrapper.select(Asset::getType));
        return all.stream().collect(
                Collectors.groupingBy(Asset::getType, Collectors.counting()));
    }

    private LambdaQueryWrapper<Asset> buildUserQueryWrapper(Long userId, Long projectId, String type, String keyword) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .eq(Asset::getUserId, userId)
                .orderByDesc(Asset::getUpdateTime);
        if (projectId != null) {
            wrapper.eq(Asset::getProjectId, projectId);
        }
        if (StrUtil.isNotBlank(type)) {
            wrapper.eq(Asset::getType, type);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(Asset::getName, keyword.trim());
        }
        return wrapper;
    }

    public List<Asset> listByOwner(Integer ownerType, Long ownerId, String type) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .eq(Asset::getOwnerType, ownerType)
                .eq(Asset::getOwnerId, ownerId)
                .orderByDesc(Asset::getCreateTime);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Asset::getType, type);
        }
        return assetMapper.selectList(wrapper);
    }

    public Asset findByProjectTypeAndName(Long projectId, String type, String name) {
        return assetMapper.selectOne(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getProjectId, projectId)
                .eq(Asset::getType, type)
                .eq(Asset::getName, name)
                .last("LIMIT 1"));
    }

    @CacheEvict(value = { "asset", "assetItem" }, allEntries = true)
    @Transactional
    public Asset create(Asset asset) {
        assetMapper.insert(asset);

        // 自动创建初始子资产，名称使用主资产名称
        AssetItem initialItem = AssetItem.builder()
                .assetId(asset.getId())
                .itemType("initial")
                .name(asset.getName())
                .sortOrder(0)
                .sourceType(asset.getSourceType() != null ? asset.getSourceType() : 1)
                .build();
        assetItemMapper.insert(initialItem);

        return asset;
    }

    @CacheEvict(value = "asset", allEntries = true)
    @Transactional
    public Asset update(Asset asset) {
        getById(asset.getId());
        assetMapper.updateById(asset);
        return asset;
    }

    @CacheEvict(value = "asset", allEntries = true)
    @Transactional
    public Asset updateForUser(Asset asset, Long userId) {
        Asset existing = getById(asset.getId());
        assertAssetOwner(existing, userId);
        assetMapper.updateById(asset);
        return asset;
    }

    @CacheEvict(value = "asset", allEntries = true)
    @Transactional
    public void delete(Long id) {
        assetMapper.deleteById(id);
    }

    @CacheEvict(value = "asset", allEntries = true)
    @Transactional
    public void deleteForUser(Long id, Long userId) {
        Asset existing = getById(id);
        assertAssetOwner(existing, userId);
        assetMapper.deleteById(id);
    }

    // ========== 子资产 ==========

    public AssetItem getItemById(Long id) {
        AssetItem item = assetItemMapper.selectById(id);
        if (item == null)
            throw new BusinessException("子资产不存在: " + id);
        return item;
    }

    public AssetItem getItemByIdForUser(Long id, Long userId) {
        AssetItem item = getItemById(id);
        Asset parent = getById(item.getAssetId());
        assertAssetOwner(parent, userId);
        return item;
    }

    @Cacheable(value = "assetItem", key = "'asset:' + #assetId")
    public List<AssetItem> listItems(Long assetId) {
        return assetItemMapper.selectList(new LambdaQueryWrapper<AssetItem>()
                .eq(AssetItem::getAssetId, assetId)
                .orderByAsc(AssetItem::getSortOrder));
    }

    public List<AssetItem> listItemsForUser(Long assetId, Long userId) {
        Asset asset = getById(assetId);
        assertAssetOwner(asset, userId);
        return listItems(assetId);
    }

    @CacheEvict(value = { "assetItem", "asset" }, allEntries = true)
    @Transactional
    public AssetItem createItem(AssetItem item) {
        assetItemMapper.insert(item);
        syncCoverIfAbsent(item);
        return item;
    }

    @CacheEvict(value = { "assetItem", "asset" }, allEntries = true)
    @Transactional
    public AssetItem createItemForUser(AssetItem item, Long userId) {
        if (item.getAssetId() == null) {
            throw new BusinessException(400, "assetId 不能为空");
        }
        Asset asset = getById(item.getAssetId());
        assertAssetOwner(asset, userId);
        assetItemMapper.insert(item);
        syncCoverIfAbsent(item);
        return item;
    }

    @CacheEvict(value = { "assetItem", "asset" }, allEntries = true)
    @Transactional
    public AssetItem updateItem(AssetItem item) {
        AssetItem existing = assetItemMapper.selectById(item.getId());
        if (existing == null)
            throw new BusinessException("子资产不存在: " + item.getId());
        assetItemMapper.updateById(item);
        // 部分更新时 item 可能缺少 assetId/imageUrl/itemType，用 existing 补全
        if (item.getAssetId() == null) {
            item.setAssetId(existing.getAssetId());
        }
        if (item.getImageUrl() == null) {
            item.setImageUrl(existing.getImageUrl());
        }
        if (item.getItemType() == null) {
            item.setItemType(existing.getItemType());
        }
        syncCoverIfAbsent(item);
        return item;
    }

    @CacheEvict(value = { "assetItem", "asset" }, allEntries = true)
    @Transactional
    public AssetItem updateItemForUser(AssetItem item, Long userId) {
        AssetItem existing = assetItemMapper.selectById(item.getId());
        if (existing == null) {
            throw new BusinessException("子资产不存在: " + item.getId());
        }
        Asset sourceAsset = getById(existing.getAssetId());
        assertAssetOwner(sourceAsset, userId);

        if (item.getAssetId() != null && !Objects.equals(item.getAssetId(), existing.getAssetId())) {
            Asset targetAsset = getById(item.getAssetId());
            assertAssetOwner(targetAsset, userId);
        }

        assetItemMapper.updateById(item);
        if (item.getAssetId() == null) {
            item.setAssetId(existing.getAssetId());
        }
        if (item.getImageUrl() == null) {
            item.setImageUrl(existing.getImageUrl());
        }
        if (item.getItemType() == null) {
            item.setItemType(existing.getItemType());
        }
        syncCoverIfAbsent(item);
        return item;
    }

    @CacheEvict(value = "assetItem", allEntries = true)
    @Transactional
    public void deleteItem(Long id) {
        assetItemMapper.deleteById(id);
    }

    @CacheEvict(value = "assetItem", allEntries = true)
    @Transactional
    public void deleteItemForUser(Long id, Long userId) {
        AssetItem existing = assetItemMapper.selectById(id);
        if (existing == null) {
            return;
        }
        Asset asset = getById(existing.getAssetId());
        assertAssetOwner(asset, userId);
        assetItemMapper.deleteById(id);
    }

    /**
     * 同步主资产封面：
     * - initial 类型子资产：新增或更新图片时，始终同步为主资产封面
     * - 其他类型子资产：仅在主资产无封面时自动填充
     */
    private void syncCoverIfAbsent(AssetItem item) {
        if (StrUtil.isBlank(item.getImageUrl()) || item.getAssetId() == null) {
            return;
        }
        Asset asset = assetMapper.selectById(item.getAssetId());
        if (asset == null) {
            return;
        }
        if ("initial".equals(item.getItemType())) {
            asset.setCoverUrl(item.getImageUrl());
            assetMapper.updateById(asset);
        } else if (StrUtil.isBlank(asset.getCoverUrl())) {
            asset.setCoverUrl(item.getImageUrl());
            assetMapper.updateById(asset);
        }
    }

    private void assertAssetOwner(Asset asset, Long userId) {
        if (asset == null || !Objects.equals(asset.getUserId(), userId)) {
            throw new BusinessException(404, "资产不存在");
        }
    }
}
