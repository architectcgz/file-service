package com.architectcgz.file.infrastructure.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 存储对象元数据
 * 封装从存储服务（S3 HeadObject / 本地文件系统）获取的文件元信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectMetadata {

    /**
     * 文件大小（字节）
     */
    private long fileSize;

    /**
     * 文件内容类型（MIME type）
     */
    private String contentType;
}
