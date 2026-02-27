package com.architectcgz.file.application.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建租户请求
 */
@Data
public class CreateTenantRequest {
    private String tenantId;
    private String tenantName;
    private Long maxStorageBytes;
    private Integer maxFileCount;
    private Long maxSingleFileSize;
    private List<String> allowedFileTypes;
    private String contactEmail;
}
