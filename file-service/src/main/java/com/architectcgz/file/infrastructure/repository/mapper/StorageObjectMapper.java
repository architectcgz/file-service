package com.architectcgz.file.infrastructure.repository.mapper;

import com.architectcgz.file.infrastructure.repository.po.StorageObjectPO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 存储对象 Mapper
 * 
 * @author Blog Team
 */
@Mapper
public interface StorageObjectMapper {
    
    /**
     * 插入存储对象
     */
    @Insert("""
        INSERT INTO storage_objects (
            id, app_id, file_hash, hash_algorithm, storage_path, file_size, 
            content_type, reference_count, created_at, updated_at
        ) VALUES (
            #{id}, #{appId}, #{fileHash}, #{hashAlgorithm}, #{storagePath}, #{fileSize},
            #{contentType}, #{referenceCount}, #{createdAt}, #{updatedAt}
        )
        """)
    int insert(StorageObjectPO storageObject);
    
    /**
     * 根据应用ID和文件哈希查询存储对象
     * 
     * @param appId 应用ID
     * @param fileHash 文件哈希值
     * @return 存储对象PO
     */
    @Select("""
        SELECT id, app_id, file_hash, hash_algorithm, storage_path, file_size,
               content_type, reference_count, created_at, updated_at
        FROM storage_objects
        WHERE app_id = #{appId} AND file_hash = #{fileHash}
        """)
    @Results(id = "storageObjectResult", value = {
        @Result(property = "id", column = "id"),
        @Result(property = "appId", column = "app_id"),
        @Result(property = "fileHash", column = "file_hash"),
        @Result(property = "hashAlgorithm", column = "hash_algorithm"),
        @Result(property = "storagePath", column = "storage_path"),
        @Result(property = "fileSize", column = "file_size"),
        @Result(property = "contentType", column = "content_type"),
        @Result(property = "referenceCount", column = "reference_count"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    StorageObjectPO selectByFileHash(@Param("appId") String appId, @Param("fileHash") String fileHash);
    
    /**
     * 根据ID查询存储对象
     */
    @Select("""
        SELECT id, app_id, file_hash, hash_algorithm, storage_path, file_size,
               content_type, reference_count, created_at, updated_at
        FROM storage_objects
        WHERE id = #{id}
        """)
    @ResultMap("storageObjectResult")
    StorageObjectPO selectById(String id);
    
    /**
     * 增加引用计数
     */
    @Update("""
        UPDATE storage_objects
        SET reference_count = reference_count + 1,
            updated_at = #{updatedAt}
        WHERE id = #{id}
        """)
    int incrementReferenceCount(@Param("id") String id, @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * 减少引用计数
     */
    @Update("""
        UPDATE storage_objects
        SET reference_count = reference_count - 1,
            updated_at = #{updatedAt}
        WHERE id = #{id} AND reference_count > 0
        """)
    int decrementReferenceCount(@Param("id") String id, @Param("updatedAt") LocalDateTime updatedAt);
    
    /**
     * 删除存储对象
     */
    @Delete("DELETE FROM storage_objects WHERE id = #{id}")
    int deleteById(String id);

    /**
     * 查询引用计数为零且超过保护窗口的存储对象（孤立对象）
     * 增加时间保护窗口，避免与正常删除流程互相干扰
     *
     * @param graceMinutes 时间保护窗口（分钟），只清理 updated_at 早于该时间的记录
     * @param limit 最大返回数量
     * @return 孤立存储对象列表
     */
    @Select("""
        SELECT id, app_id, file_hash, hash_algorithm, storage_path, file_size,
               content_type, reference_count, created_at, updated_at
        FROM storage_objects
        WHERE reference_count <= 0
          AND updated_at < NOW() - CAST(#{graceMinutes} || ' minutes' AS INTERVAL)
        ORDER BY updated_at ASC
        LIMIT #{limit}
        """)
    @ResultMap("storageObjectResult")
    List<StorageObjectPO> selectZeroReferenceObjects(@Param("graceMinutes") int graceMinutes,
                                                      @Param("limit") int limit);
}

