package com.platform.fileservice.client.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 秒传检查响应模型
 * 指示文件是否已存在，如果存在则提供元数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstantUploadCheckResponse {
    /**
     * 文件是否已存在
     */
    private Boolean exists;
    
    /**
     * 唯一文件标识符（仅当exists为true时存在）
     */
    private String fileId;
    
    /**
     * 文件访问URL（仅当exists为true时存在）
     */
    private String url;
    
    /**
     * 文件元数据（仅当exists为true时存在）
     */
    private FileUploadResponse fileInfo;
}
