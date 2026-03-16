package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.service.multipart.bridge.MultipartUploadCoreBridgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultipartUploadAbortCommandService 单元测试")
class MultipartUploadAbortCommandServiceTest {

    @Mock
    private MultipartUploadCoreBridgeService multipartUploadCoreBridgeService;

    private MultipartUploadAbortCommandService multipartUploadAbortCommandService;

    @BeforeEach
    void setUp() {
        multipartUploadAbortCommandService = new MultipartUploadAbortCommandService(multipartUploadCoreBridgeService);
    }

    @Test
    @DisplayName("中止分片上传应委托给 core bridge")
    void abortUpload_shouldDelegateToCoreBridge() {
        multipartUploadAbortCommandService.abortUpload("blog", "task-001", "user-123");

        verify(multipartUploadCoreBridgeService).abortUpload("blog", "task-001", "user-123");
    }
}
