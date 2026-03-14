package com.architectcgz.file.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 完成直传上传请求
 */
@Data
public class DirectUploadCompleteRequest {
    
    /**
     * 上传任务ID
     */
    @NotBlank(message = "任务ID不能为空")
    private String taskId;
    
    /**
     * 文件内容类型
     */
    @NotBlank(message = "文件类型不能为空")
    private String contentType;
    
    /**
     * 已上传的分片信息列表
     */
    private List<PartInfo> parts;
    
    /**
     * 分片信息
     */
    @Data
    public static class PartInfo {
        /**
         * 分片编号
         */
        private Integer partNumber;
        
        /**
         * S3 返回的 ETag
         */
        private String etag;
    }
}
