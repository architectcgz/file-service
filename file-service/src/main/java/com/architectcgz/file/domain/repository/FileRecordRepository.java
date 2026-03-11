package com.architectcgz.file.domain.repository;

import com.architectcgz.file.application.dto.FileQuery;
import com.architectcgz.file.domain.model.ContentTypeCount;
import com.architectcgz.file.domain.model.StorageStatisticsAggregation;
import com.architectcgz.file.domain.model.TenantStorageAggregation;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;

import java.util.List;
import java.util.Optional;

/**
 * 文件记录仓储接口
 */
public interface FileRecordRepository {
    
    /**
     * 保存文件记录
     * 
     * @param fileRecord 文件记录
     * @return 保存后的文件记录
     */
    FileRecord save(FileRecord fileRecord);
    
    /**
     * 根据ID查找文件记录
     * 
     * @param id 文件记录ID
     * @return 文件记录（如果存在）
     */
    Optional<FileRecord> findById(String id);
    
    /**
     * 根据应用ID、用户ID和文件哈希查找已完成的文件记录
     * 用于检查用户是否已上传相同文件（秒传功能）
     * 
     * @param appId 应用ID
     * @param userId 用户ID
     * @param fileHash 文件哈希值
     * @return 已完成的文件记录（如果存在）
     */
    Optional<FileRecord> findByUserIdAndFileHash(String appId, String userId, String fileHash);
    
    /**
     * 根据应用ID和文件哈希查找已完成的文件记录（跨用户）
     * 用于检查是否有其他用户已上传相同文件（秒传功能）
     * 
     * @param appId 应用ID
     * @param fileHash 文件哈希值
     * @return 已完成的文件记录（如果存在）
     */
    Optional<FileRecord> findCompletedByAppIdAndFileHash(String appId, String fileHash);
    
    /**
     * 更新文件记录状态
     * 
     * @param id 文件记录ID
     * @param status 新状态
     * @return 是否更新成功
     */
    boolean updateStatus(String id, FileStatus status);
    
    /**
     * 更新文件访问级别
     * 
     * @param id 文件记录ID
     * @param accessLevel 新的访问级别
     * @return 是否更新成功
     */
    boolean updateAccessLevel(String id, AccessLevel accessLevel);

    /**
     * 更新文件记录绑定的存储对象和访问级别
     *
     * @param id 文件记录ID
     * @param storageObjectId 新的存储对象ID
     * @param storagePath 新的存储路径
     * @param accessLevel 新的访问级别
     * @return 是否更新成功
     */
    boolean updateStorageBindingAndAccessLevel(String id, String storageObjectId, String storagePath,
                                               AccessLevel accessLevel);
    
    /**
     * 根据查询条件查找文件记录列表
     * 
     * @param query 查询条件
     * @return 文件记录列表
     */
    List<FileRecord> findByQuery(FileQuery query);
    
    /**
     * 根据查询条件统计文件记录数量
     * 
     * @param query 查询条件
     * @return 文件记录数量
     */
    long countByQuery(FileQuery query);
    
    /**
     * 删除文件记录
     * 
     * @param id 文件记录ID
     * @return 是否删除成功
     */
    boolean deleteById(String id);

    /**
     * 存储统计聚合查询（SQL 层完成 COUNT/SUM）
     *
     * @param appId 应用ID（可选，为 null 时查询所有租户）
     * @return 聚合统计结果
     */
    StorageStatisticsAggregation getStorageStatisticsAggregation(String appId);

    /**
     * 按内容类型分组统计文件数量（SQL 层完成 GROUP BY）
     *
     * @param appId 应用ID（可选，为 null 时查询所有租户）
     * @return 各内容类型的文件计数列表
     */
    List<ContentTypeCount> getFileCountByContentType(String appId);

    /**
     * 按租户分组统计存储空间（SQL 层完成 GROUP BY）
     *
     * @return 各租户的存储空间聚合列表
     */
    List<TenantStorageAggregation> getStorageByTenant();
}
