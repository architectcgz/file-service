package com.architectcgz.file.domain.repository;

import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 上传任务仓储接口
 * 
 * 职责：仅负责上传任务（UploadTask）的持久化操作
 * 不包含分片（UploadPart）相关操作，分片操作由 UploadPartRepository 负责
 */
public interface UploadTaskRepository {
    
    /**
     * 保存上传任务
     */
    void save(UploadTask task);
    
    /**
     * 根据ID查询上传任务
     */
    Optional<UploadTask> findById(String id);
    
    /**
     * 根据应用ID、用户ID和文件哈希查询未完成的上传任务
     * 用于断点续传匹配
     * 
     * @param appId 应用ID
     * @param userId 用户ID
     * @param fileHash 文件哈希值
     * @return 上传任务（如果存在）
     */
    Optional<UploadTask> findByUserIdAndFileHash(String appId, String userId, String fileHash);
    
    /**
     * 查询过期的上传任务
     */
    List<UploadTask> findExpiredTasks(LocalDateTime now);
    
    /**
     * 更新任务状态
     */
    void updateStatus(String taskId, UploadTaskStatus status);
    
    /**
     * 根据应用ID和用户ID查询上传任务列表
     * 
     * @param appId 应用ID
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 上传任务列表
     */
    List<UploadTask> findByUserId(String appId, String userId, int limit);
}
