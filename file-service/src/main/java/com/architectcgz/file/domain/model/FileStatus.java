package com.architectcgz.file.domain.model;

/**
 * 文件状态枚举
 * 用于标识文件记录的当前状态
 */
public enum FileStatus {
    
    /**
     * 已完成- 文件上传成功并可用
     */
    COMPLETED,
    
    /**
     * 已删除- 文件已被软删除
     */
    DELETED
}
