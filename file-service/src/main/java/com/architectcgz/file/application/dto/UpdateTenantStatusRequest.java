package com.architectcgz.file.application.dto;

import com.architectcgz.file.domain.model.TenantStatus;
import lombok.Data;

/**
 * 更新租户状态请求
 */
@Data
public class UpdateTenantStatusRequest {
    private TenantStatus status;
}
