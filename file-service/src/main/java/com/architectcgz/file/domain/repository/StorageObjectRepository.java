package com.architectcgz.file.domain.repository;

import com.architectcgz.file.domain.model.StorageObject;

import java.util.List;
import java.util.Optional;

/**
 * 存储对象仓储接口
 * 用于管理文件去重和引用计数
 */
public interface StorageObjectRepository {
    
    /**
     * 保存存储对象
     *
     * @param storageObject 存储对象
     * @return 保存后的存储对象
     */
    StorageObject save(StorageObject storageObject);
    
    /**
     * 根据应用ID和文件哈希查找存储对象
     *
     * @param appId 应用ID
     * @param fileHash 文件哈希值
     * @return 存储对象（如果存在）
     */
    Optional<StorageObject> findByFileHash(String appId, String fileHash);
    
    /**
     * 根据ID查找存储对象
     *
     * @param id 存储对象ID
     * @return 存储对象（如果存在）
     */
    Optional<StorageObject> findById(String id);
    
    /**
     * 增加引用计数
     *
     * @param id 存储对象ID
     * @return 是否成功
     */
    boolean incrementReferenceCount(String id);
    
    /**
     * 减少引用计数
     *
     * @param id 存储对象ID
     * @return 是否成功
     */
    boolean decrementReferenceCount(String id);
    
    /**
     * 删除存储对象
     *
     * @param id 存储对象ID
     * @return 是否成功
     */
    boolean deleteById(String id);

    /**
     * 查找引用计数为零且超过保护窗口的存储对象（孤立对象）
     * 用于定时清理任务，分批查询避免一次加载过多数据。
     * 增加时间保护窗口，避免与正常删除流程互相干扰。
     *
     * @param graceMinutes 时间保护窗口（分钟），只清理 updated_at 早于该时间的记录
     * @param limit 最大返回数量
     * @return 引用计数为零的存储对象列表
     */
    List<StorageObject> findZeroReferenceObjects(int graceMinutes, int limit);
}
