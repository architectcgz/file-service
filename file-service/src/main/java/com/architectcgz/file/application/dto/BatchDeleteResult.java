package com.architectcgz.file.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量删除结果响应对象
 * 包含批量删除操作的执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDeleteResult {
    
    /**
     * 请求删除的总数
     */
    private int totalRequested;
    
    /**
     * 成功删除的数量
     */
    private int successCount;
    
    /**
     * 失败的数量
     */
    private int failureCount;
    
    /**
     * 失败详情列表
     */
    @Builder.Default
    private List<DeleteFailure> failures = new ArrayList<>();
    
    /**
     * 删除失败详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteFailure {
        
        /**
         * 文件 ID
         */
        private String fileId;
        
        /**
         * 失败原因
         */
        private String reason;
    }
}
