package com.architectcgz.file.application.service.direct.command;

import com.architectcgz.file.application.dto.DirectUploadInitRequest;
import com.architectcgz.file.application.dto.DirectUploadInitResponse;
import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.direct.bridge.DirectUploadCoreBridgeService;
import com.architectcgz.file.domain.model.Tenant;
import com.architectcgz.file.domain.model.TenantStatus;
import com.architectcgz.file.domain.service.TenantDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DirectUploadInitCommandService 单元测试")
class DirectUploadInitCommandServiceTest {

    @Mock
    private FileTypeValidator fileTypeValidator;
    @Mock
    private TenantDomainService tenantDomainService;
    @Mock
    private DirectUploadCoreBridgeService directUploadCoreBridgeService;

    private DirectUploadInitCommandService directUploadInitCommandService;

    @BeforeEach
    void setUp() {
        directUploadInitCommandService = new DirectUploadInitCommandService(
                fileTypeValidator,
                tenantDomainService,
                directUploadCoreBridgeService
        );
    }

    @Test
    @DisplayName("初始化直传上传时应先做轻量预检查再委托给 core bridge")
    void initDirectUpload_shouldValidateAndDelegateToCoreBridge() {
        DirectUploadInitRequest request = new DirectUploadInitRequest();
        request.setFileName("report.pdf");
        request.setFileSize(1024L);
        request.setContentType("application/pdf");
        request.setFileHash("hash-123");

        DirectUploadInitResponse response = DirectUploadInitResponse.builder()
                .taskId("task-001")
                .uploadId("upload-001")
                .storagePath("blog/2026/03/14/user-123/files/report.pdf")
                .chunkSize(5 * 1024 * 1024)
                .totalParts(1)
                .isResume(false)
                .isInstantUpload(false)
                .build();
        Tenant tenant = new Tenant();
        tenant.setTenantId("blog");
        tenant.setStatus(TenantStatus.ACTIVE);

        when(tenantDomainService.validateUploadPrerequisites("blog", 1024L)).thenReturn(tenant);
        when(directUploadCoreBridgeService.initDirectUpload("blog", request, "user-123"))
                .thenReturn(response);

        DirectUploadInitResponse actual =
                directUploadInitCommandService.initDirectUpload("blog", request, "user-123");

        assertThat(actual).isSameAs(response);
        verify(tenantDomainService).validateUploadPrerequisites("blog", 1024L);
        verify(fileTypeValidator).validateFile("report.pdf", "application/pdf", 1024L);
        verify(directUploadCoreBridgeService).initDirectUpload("blog", request, "user-123");
    }
}
