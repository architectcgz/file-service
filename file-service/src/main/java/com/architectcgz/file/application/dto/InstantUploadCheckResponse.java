package com.architectcgz.file.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 秒传检查响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstantUploadCheckResponse {
    
    /**
     * 是否可以秒传
     */
    private Boolean instantUpload;
    
    /**
     * 文件ID（秒传成功时返回）
     */
    private String fileId;
    
    /**
     * 文件URL（秒传成功时返回）
     */
    private String url;
    
    /**
     * 是否需要上传（秒传失败时返回）
     */
    private Boolean needUpload;
    
    /**
     * 提示信息
     */
    private String message;
    
    /**
     * 创建秒传成功响应
     */
    public static InstantUploadCheckResponse success(String fileId, String url) {
        return InstantUploadCheckResponse.builder()
                .instantUpload(true)
                .fileId(fileId)
                .url(url)
                .needUpload(false)
                .message("文件已存在，秒传成功")
                .build();
    }
    
    /**
     * 创建需要上传响应
     */
    public static InstantUploadCheckResponse needUpload() {
        return InstantUploadCheckResponse.builder()
                .instantUpload(false)
                .needUpload(true)
                .message("文件不存在，需要上传")
                .build();
    }
}
