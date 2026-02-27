package com.architectcgz.file.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 获取分片上传 URL 响应
 */
@Data
@Builder
public class DirectUploadPartUrlResponse {
    
    /**
     * 上传任务ID
     */
    private String taskId;
    
    /**
     * 分片上传 URL 列表
     */
    private List<PartUrl> partUrls;
    
    /**
     * 分片上传 URL 信息
     */
    @Data
    @Builder
    public static class PartUrl {
        /**
         * 分片编号
         */
        private Integer partNumber;
        
        /**
         * 预签名上传 URL
         */
        private String uploadUrl;
        
        /**
         * URL 过期时间（秒）
         */
        private Integer expiresIn;
    }
}
