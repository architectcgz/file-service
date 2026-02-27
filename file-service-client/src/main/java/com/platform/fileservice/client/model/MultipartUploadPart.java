package com.platform.fileservice.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 分片上传中单个已上传分片的模型
 * 包含完成上传所需的分片编号和ETag
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultipartUploadPart {
    /**
     * 分片编号（从1开始的索引）
     */
    private Integer partNumber;
    
    /**
     * 上传分片后S3返回的ETag
     */
    private String etag;
}
