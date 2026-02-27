package com.architectcgz.file.domain.repository;

import com.architectcgz.file.domain.model.UploadPart;

import java.util.List;

/**
 * 分片上传仓储接口
 * 负责分片状态的记录、查询和同步
 * 
 * 使用 Redis Bitmap 作为快速缓存层，配合定期同步和故障回退机制，
 * 将数据库写入操作减少 90% 以上，同时保证数据可靠性和一致性。
 * 
 * Requirements: 1.1, 2.1, 2.2, 4.1, 8.3
 */
public interface UploadPartRepository {
    
    /**
     * 记录分片上传
     * 优先使用 Redis Bitmap，失败时回退到数据库
     * 
     * 实现要点:
     * - 检查 Bitmap 功能是否启用
     * - 写入 Redis Bitmap (SETBIT)
     * - 设置 TTL (24 小时)
     * - 判断是否需要触发定期同步
     * - Redis 失败时回退到数据库
     * 
     * @param part 分片信息
     * 
     * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 3.1, 3.5, 5.1, 5.2, 5.5
     */
    void savePart(UploadPart part);
    
    /**
     * 查询已完成的分片数量
     * 优先从 Redis Bitmap 查询，失败时查询数据库
     * 
     * 实现要点:
     * - 优先从 Redis Bitmap 查询 (BITCOUNT)
     * - Redis 失败时回退到数据库
     * - 记录缓存命中/未命中
     * 
     * @param taskId 任务ID
     * @return 已完成分片数量
     * 
     * Requirements: 2.1, 2.4, 8.2
     */
    int countCompletedParts(String taskId);
    
    /**
     * 查询已完成的分片编号列表
     * 优先从 Redis Bitmap 查询，失败时查询数据库
     * 
     * 实现要点:
     * - 使用 BITCOUNT 获取总数
     * - 遍历 Bitmap 获取所有已完成分片编号
     * - 优化遍历性能（提前终止）
     * - Redis 失败时回退到数据库
     * 
     * @param taskId 任务ID
     * @return 分片编号列表（从小到大排序）
     * 
     * Requirements: 2.2, 8.2
     */
    List<Integer> findCompletedPartNumbers(String taskId);
    
    /**
     * 完成上传时全量同步到数据库
     * 
     * 实现要点:
     * - 从 Bitmap 获取所有分片
     * - 批量插入到数据库
     * - 删除 Bitmap 释放内存
     * - 失败时抛出异常并保留 Bitmap
     * 
     * @param taskId 任务ID
     * @param parts 所有分片信息
     * @throws RuntimeException 同步失败时抛出异常
     * 
     * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5
     */
    void syncAllPartsToDatabase(String taskId, List<UploadPart> parts);
    
    /**
     * 从数据库加载分片状态到 Bitmap（用于断点续传）
     * 
     * 实现要点:
     * - 从数据库查询已完成分片
     * - 重建 Bitmap
     * - 设置 TTL
     * 
     * @param taskId 任务ID
     * 
     * Requirements: 8.3, 8.4
     */
    void loadPartsFromDatabase(String taskId);
}
