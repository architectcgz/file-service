package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.service.multipart.bridge.MultipartUploadCoreBridgeService;
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
@DisplayName("MultipartUploadCompleteCommandService 单元测试")
class MultipartUploadCompleteCommandServiceTest {

    @Mock
    private MultipartUploadCoreBridgeService multipartUploadCoreBridgeService;

    private MultipartUploadCompleteCommandService multipartUploadCompleteCommandService;

    @BeforeEach
    void setUp() {
        multipartUploadCompleteCommandService = new MultipartUploadCompleteCommandService(multipartUploadCoreBridgeService);
    }

    @Test
    @DisplayName("完成分片上传应委托给 core bridge")
    void completeUpload_shouldDelegateToCoreBridge() {
        when(multipartUploadCoreBridgeService.completeUpload("blog", "task-001", "user-123"))
                .thenReturn("file-001");

        String actual = multipartUploadCompleteCommandService.completeUpload("blog", "task-001", "user-123");

        assertThat(actual).isEqualTo("file-001");
        verify(multipartUploadCoreBridgeService).completeUpload("blog", "task-001", "user-123");
    }
}
