package com.architectcgz.file.infrastructure.repository.mapper;

import com.architectcgz.file.infrastructure.repository.po.UploadPartPO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 上传分片 MyBatis Mapper
 */
@Mapper
public interface UploadPartMapper {
    
    /**
     * 插入上传分片
     * 
     * @param part 上传分片PO
     */
    @Insert("""
        INSERT INTO upload_parts (
            id, task_id, part_number, etag, size, uploaded_at
        ) VALUES (
            #{id}, #{taskId}, #{partNumber}, #{etag}, #{size}, #{uploadedAt}
        )
    """)
    void insert(UploadPartPO part);
    
    /**
     * 插入上传分片（如果已存在则忽略）
     * 使用 ON CONFLICT DO NOTHING 保证幂等性
     *
     * @param part 上传分片PO
     */
    @Insert("""
        INSERT INTO upload_parts (
            id, task_id, part_number, etag, size, uploaded_at
        ) VALUES (
            #{id}, #{taskId}, #{partNumber}, #{etag}, #{size}, #{uploadedAt}
        )
        ON CONFLICT (task_id, part_number) DO NOTHING
    """)
    void insertOrIgnore(UploadPartPO part);

    /**
     * 插入或更新上传分片（upsert）
     * 冲突时更新 etag 和 size，确保幂等查询能获取到最新的 ETag
     *
     * @param part 上传分片PO
     */
    @Insert("""
        INSERT INTO upload_parts (
            id, task_id, part_number, etag, size, uploaded_at
        ) VALUES (
            #{id}, #{taskId}, #{partNumber}, #{etag}, #{size}, #{uploadedAt}
        )
        ON CONFLICT (task_id, part_number) DO UPDATE SET
            etag = EXCLUDED.etag,
            size = EXCLUDED.size,
            uploaded_at = EXCLUDED.uploaded_at
    """)
    void upsert(UploadPartPO part);
    
    /**
     * 批量插入上传分片
     * 使用 XML 映射文件实现
     * 
     * @param parts 上传分片列表
     */
    default void batchInsert(List<UploadPartPO> parts) {
        if (parts == null || parts.isEmpty()) {
            return;
        }
        doBatchInsert(parts);
    }

    /**
     * 执行批量插入上传分片
     *
     * @param parts 上传分片列表
     */
    void doBatchInsert(@Param("parts") List<UploadPartPO> parts);
    
    /**
     * 根据任务ID查询所有分片
     * 
     * @param taskId 任务ID
     * @return 分片列表
     */
    @Select("""
        SELECT id, task_id, part_number, etag, size, uploaded_at
        FROM upload_parts
        WHERE task_id = #{taskId}
        ORDER BY part_number ASC
    """)
    @Results(id = "uploadPartResult", value = {
        @Result(property = "id", column = "id"),
        @Result(property = "taskId", column = "task_id"),
        @Result(property = "partNumber", column = "part_number"),
        @Result(property = "etag", column = "etag"),
        @Result(property = "size", column = "size"),
        @Result(property = "uploadedAt", column = "uploaded_at")
    })
    List<UploadPartPO> selectByTaskId(String taskId);
    
    /**
     * 根据任务ID查询所有分片编号
     * 
     * @param taskId 任务ID
     * @return 分片编号列表
     */
    @Select("""
        SELECT part_number
        FROM upload_parts
        WHERE task_id = #{taskId}
        ORDER BY part_number ASC
    """)
    List<Integer> findPartNumbersByTaskId(@Param("taskId") String taskId);
    
    /**
     * 根据任务ID和分片编号查询单个分片
     * 用于幂等性检查时获取已有分片的 ETag
     *
     * @param taskId 任务ID
     * @param partNumber 分片编号
     * @return 分片记录，不存在时返回 null
     */
    @Select("""
        SELECT id, task_id, part_number, etag, size, uploaded_at
        FROM upload_parts
        WHERE task_id = #{taskId} AND part_number = #{partNumber}
    """)
    @ResultMap("uploadPartResult")
    UploadPartPO selectByTaskIdAndPartNumber(@Param("taskId") String taskId, @Param("partNumber") int partNumber);

    /**
     * 统计任务的已完成分片数量
     *
     * @param taskId 任务ID
     * @return 已完成分片数量
     */
    @Select("""
        SELECT COUNT(*)
        FROM upload_parts
        WHERE task_id = #{taskId}
    """)
    int countByTaskId(String taskId);
    
    /**
     * 删除任务的所有分片
     * 
     * @param taskId 任务ID
     * @return 影响的行数
     */
    @Delete("""
        DELETE FROM upload_parts
        WHERE task_id = #{taskId}
    """)
    int deleteByTaskId(String taskId);
}
