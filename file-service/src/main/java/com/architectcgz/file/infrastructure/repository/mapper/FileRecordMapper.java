package com.architectcgz.file.infrastructure.repository.mapper;

import com.architectcgz.file.application.dto.FileQuery;
import com.architectcgz.file.domain.model.ContentTypeCount;
import com.architectcgz.file.domain.model.StorageStatisticsAggregation;
import com.architectcgz.file.domain.model.TenantStorageAggregation;
import com.architectcgz.file.infrastructure.repository.po.FileRecordPO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件记录 MyBatis Mapper
 */
@Mapper
public interface FileRecordMapper {
    
    /**
     * 插入文件记录
     * 
     * @param fileRecord 文件记录PO
     */
    @Insert("""
        INSERT INTO file_records (
            id, app_id, user_id, storage_object_id, original_name, storage_path, file_size, 
            content_type, file_hash, access_level, status, created_at, updated_at
        ) VALUES (
            #{id}, #{appId}, #{userId}, #{storageObjectId}, #{originalFilename}, #{storagePath}, #{fileSize},
            #{contentType}, #{fileHash}, #{accessLevel}, #{status}, #{createdAt}, #{updatedAt}
        )
    """)
    void insert(FileRecordPO fileRecord);
    
    /**
     * 根据ID查询文件记录
     * 
     * @param id 文件记录ID
     * @return 文件记录PO
     */
    @Select("""
        SELECT id, app_id, user_id, storage_object_id, original_name, storage_path, file_size,
               content_type, file_hash, access_level, status, created_at, updated_at
        FROM file_records
        WHERE id = #{id}
    """)
    @Results(id = "fileRecordResult", value = {
        @Result(property = "id", column = "id"),
        @Result(property = "appId", column = "app_id"),
        @Result(property = "userId", column = "user_id"),
        @Result(property = "storageObjectId", column = "storage_object_id"),
        @Result(property = "originalFilename", column = "original_name"),
        @Result(property = "storagePath", column = "storage_path"),
        @Result(property = "fileSize", column = "file_size"),
        @Result(property = "contentType", column = "content_type"),
        @Result(property = "fileHash", column = "file_hash"),
        @Result(property = "accessLevel", column = "access_level"),
        @Result(property = "status", column = "status"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    FileRecordPO selectById(String id);
    
    /**
     * 根据应用ID、用户ID和文件哈希查询文件记录
     * 
     * @param appId 应用ID
     * @param userId 用户ID
     * @param fileHash 文件哈希值
     * @return 文件记录PO
     */
    @Select("""
        SELECT id, app_id, user_id, storage_object_id, original_name, storage_path, file_size,
               content_type, file_hash, access_level, status, created_at, updated_at
        FROM file_records
        WHERE app_id = #{appId} AND user_id = #{userId} AND file_hash = #{fileHash}
        ORDER BY created_at DESC
        LIMIT 1
    """)
    @ResultMap("fileRecordResult")
    FileRecordPO selectByUserIdAndFileHash(@Param("appId") String appId, @Param("userId") String userId, @Param("fileHash") String fileHash);
    
    /**
     * 根据应用ID和文件哈希查询已完成的文件记录（跨用户）
     * 用于秒传功能
     * 
     * @param appId 应用ID
     * @param fileHash 文件哈希值
     * @return 文件记录PO
     */
    @Select("""
        SELECT id, app_id, user_id, storage_object_id, original_name, storage_path, file_size,
               content_type, file_hash, access_level, status, created_at, updated_at
        FROM file_records
        WHERE app_id = #{appId} AND file_hash = #{fileHash} AND status = 'COMPLETED'
        ORDER BY created_at DESC
        LIMIT 1
    """)
    @ResultMap("fileRecordResult")
    FileRecordPO selectCompletedByAppIdAndFileHash(@Param("appId") String appId, @Param("fileHash") String fileHash);
    
    /**
     * 更新文件记录状态
     * 
     * @param id 文件记录ID
     * @param status 新状态
     * @param updatedAt 更新时间
     * @return 影响的行数
     */
    @Update("""
        UPDATE file_records
        SET status = #{status}, updated_at = #{updatedAt}
        WHERE id = #{id}
    """)
    int updateStatus(@Param("id") String id, @Param("status") String status, @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * 更新文件访问级别
     * 
     * @param id 文件记录ID
     * @param accessLevel 新的访问级别
     * @param updatedAt 更新时间
     * @return 影响的行或
     */
    @Update("""
        UPDATE file_records
        SET access_level = #{accessLevel}, updated_at = #{updatedAt}
        WHERE id = #{id}
    """)
    int updateAccessLevel(@Param("id") String id, @Param("accessLevel") String accessLevel, @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * 根据查询条件查找文件记录列表
     * 使用 XML 映射文件实现复杂查询
     * 
     * @param query 查询条件
     * @return 文件记录PO列表
     */
    List<FileRecordPO> selectByQuery(FileQuery query);
    
    /**
     * 根据查询条件统计文件记录数量
     * 使用 XML 映射文件实现复杂查询
     * 
     * @param query 查询条件
     * @return 文件记录数量
     */
    long countByQuery(FileQuery query);
    
    /**
     * 删除文件记录
     * 
     * @param id 文件记录ID
     * @return 影响的行数
     */
    @Delete("""
        DELETE FROM file_records
        WHERE id = #{id}
    """)
    int deleteById(String id);

    /**
     * 存储统计聚合查询
     * 在 SQL 层完成 COUNT/SUM 计算，避免全量加载文件记录到内存
     *
     * @return 聚合统计结果（总文件数、总存储空间、公开/私有文件数）
     */
    StorageStatisticsAggregation selectStorageStatistics();

    /**
     * 按内容类型分组统计文件数量
     * 在 SQL 层完成 GROUP BY 聚合，避免全量加载
     *
     * @return 各内容类型的文件计数列表
     */
    List<ContentTypeCount> selectFileCountByContentType();

    /**
     * 按租户分组统计存储空间
     * 在 SQL 层完成 GROUP BY 聚合，避免全量加载
     *
     * @return 各租户的存储空间聚合列表
     */
    List<TenantStorageAggregation> selectStorageByTenant();
}
