package com.architectcgz.file.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 预签名直传进度响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectUploadProgressResponse {

    /**
     * 上传任务 ID
     */
    private String taskId;

    /**
     * 总分片数
     */
    private Integer totalParts;

    /**
     * 已完成分片数
     */
    private Integer completedParts;

    /**
     * 已上传字节数（估算值）
     */
    private Long uploadedBytes;

    /**
     * 文件总字节数
     */
    private Long totalBytes;

    /**
     * 进度百分比
     */
    private Integer percentage;

    /**
     * 已完成分片编号列表
     */
    private List<Integer> completedPartNumbers;

    /**
     * 已完成分片信息（包含 ETag）
     */
    private List<DirectUploadInitResponse.PartInfo> completedPartInfos;
}
