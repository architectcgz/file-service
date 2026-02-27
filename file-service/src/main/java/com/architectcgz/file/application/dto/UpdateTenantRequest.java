package com.architectcgz.file.application.dto;

import lombok.Data;

import java.util.List;

/**
 * 更新租户请求
 */
@Data
public class UpdateTenantRequest {
    private String tenantName;
    private Long maxStorageBytes;
    private Integer maxFileCount;
    private Long maxSingleFileSize;
    private List<String> allowedFileTypes;
    private String contactEmail;
}
