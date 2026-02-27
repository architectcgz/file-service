package com.architectcgz.file.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 直传上传初始化响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class DirectUploadInitResponse {
    
    /**
     * 上传任务ID
     */
    private String taskId;
    
    /**
     * S3 multipart upload ID
     */
    private String uploadId;
    
    /**
     * 存储路径
     */
    private String storagePath;
    
    /**
     * 分片大小（字节）
     */
    private Integer chunkSize;
    
    /**
     * 总分片数
     */
    private Integer totalParts;
    
    /**
     * 已完成的分片编号列表（断点续传时使用）
     */
    @Builder.Default
    private List<Integer> completedParts = new ArrayList<>();
    
    /**
     * 已完成分片的完整信息（包括 ETag，用于完成上传）
     */
    @Builder.Default
    private List<PartInfo> completedPartInfos = new ArrayList<>();
    
    /**
     * 是否为断点续传
     */
    @Builder.Default
    private Boolean isResume = false;
    
    /**
     * 是否为秒传（文件已存在）
     */
    @Builder.Default
    private Boolean isInstantUpload = false;
    
    /**
     * 文件ID（秒传时返回）
     */
    private String fileId;
    
    /**
     * 文件访问URL（秒传时返回）
     */
    private String fileUrl;
    
    /**
     * 分片信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartInfo {
        /**
         * 分片编号
         */
        private Integer partNumber;
        
        /**
         * ETag
         */
        private String etag;
    }
}
