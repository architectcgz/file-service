package com.architectcgz.file.infrastructure.repository.mapper;

import com.architectcgz.file.infrastructure.repository.po.UploadTaskPO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 上传任务 MyBatis Mapper
 */
@Mapper
public interface UploadTaskMapper {
    
    /**
     * 插入上传任务
     * 
     * @param task 上传任务PO
     */
    @Insert("""
        INSERT INTO upload_tasks (
            id, app_id, user_id, file_name, file_size, file_hash, content_type, storage_path,
            upload_id, total_chunks, chunk_size, status, created_at, updated_at, expires_at
        ) VALUES (
            #{id}, #{appId}, #{userId}, #{fileName}, #{fileSize}, #{fileHash}, #{contentType}, #{storagePath},
            #{uploadId}, #{totalParts}, #{chunkSize}, #{status}, #{createdAt}, #{updatedAt}, #{expiresAt}
        )
    """)
    void insert(UploadTaskPO task);
    
    /**
     * 根据ID查询上传任务
     * 
     * @param id 任务ID
     * @return 上传任务PO
     */
    @Select("""
        SELECT id, app_id, user_id, file_name, file_size, file_hash, content_type, storage_path,
               upload_id, total_chunks, chunk_size, status, created_at, updated_at, expires_at
        FROM upload_tasks
        WHERE id = #{id}
    """)
    @Results(id = "uploadTaskResult", value = {
        @Result(property = "id", column = "id"),
        @Result(property = "appId", column = "app_id"),
        @Result(property = "userId", column = "user_id"),
        @Result(property = "fileName", column = "file_name"),
        @Result(property = "fileSize", column = "file_size"),
        @Result(property = "fileHash", column = "file_hash"),
        @Result(property = "contentType", column = "content_type"),
        @Result(property = "storagePath", column = "storage_path"),
        @Result(property = "uploadId", column = "upload_id"),
        @Result(property = "totalParts", column = "total_chunks"),
        @Result(property = "chunkSize", column = "chunk_size"),
        @Result(property = "status", column = "status"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at"),
        @Result(property = "expiresAt", column = "expires_at")
    })
    UploadTaskPO selectById(String id);
    
    /**
     * 根据应用ID、用户ID和文件哈希查询未完成的上传任务
     * 
     * @param appId 应用ID
     * @param userId 用户ID
     * @param fileHash 文件哈希值
     * @return 上传任务PO
     */
    @Select("""
        SELECT id, app_id, user_id, file_name, file_size, file_hash, content_type, storage_path,
               upload_id, total_chunks, chunk_size, status, created_at, updated_at, expires_at
        FROM upload_tasks
        WHERE app_id = #{appId} AND user_id = #{userId} AND file_hash = #{fileHash} AND status = 'uploading'
        ORDER BY created_at DESC
        LIMIT 1
    """)
    @ResultMap("uploadTaskResult")
    UploadTaskPO selectByUserIdAndFileHash(@Param("appId") String appId, @Param("userId") String userId, @Param("fileHash") String fileHash);
    
    /**
     * 查询过期的上传任或
     * 
     * @param now 当前时间
     * @return 过期任务列表
     */
    @Select("""
        SELECT id, app_id, user_id, file_name, file_size, file_hash, content_type, storage_path,
               upload_id, total_chunks, chunk_size, status, created_at, updated_at, expires_at
        FROM upload_tasks
        WHERE status = 'uploading' AND expires_at < #{now}
        ORDER BY expires_at ASC
        LIMIT 100
    """)
    @ResultMap("uploadTaskResult")
    List<UploadTaskPO> selectExpiredTasks(@Param("now") LocalDateTime now);
    
    /**
     * 更新任务状态
     * 
     * @param id 任务ID
     * @param status 新状或
     * @param updatedAt 更新时间
     * @return 影响的行或
     */
    @Update("""
        UPDATE upload_tasks
        SET status = #{status}, updated_at = #{updatedAt}
        WHERE id = #{id}
    """)
    int updateStatus(@Param("id") String id, @Param("status") String status, @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * 根据应用ID和用户ID查询上传任务列表
     * 
     * @param appId 应用ID
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 上传任务列表
     */
    @Select("""
        SELECT id, app_id, user_id, file_name, file_size, file_hash, content_type, storage_path,
               upload_id, total_chunks, chunk_size, status, created_at, updated_at, expires_at
        FROM upload_tasks
        WHERE app_id = #{appId} AND user_id = #{userId}
        ORDER BY created_at DESC
        LIMIT #{limit}
    """)
    @ResultMap("uploadTaskResult")
    List<UploadTaskPO> selectByUserId(@Param("appId") String appId, @Param("userId") String userId, @Param("limit") int limit);
}
