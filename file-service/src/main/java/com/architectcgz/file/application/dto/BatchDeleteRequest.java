package com.architectcgz.file.application.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量删除请求对象
 */
@Data
public class BatchDeleteRequest {
    
    /**
     * 要删除的文件ID列表
     */
    private List<String> fileIds;
}
