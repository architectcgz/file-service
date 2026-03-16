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
@DisplayName("MultipartPartUploadCommandService 单元测试")
class MultipartPartUploadCommandServiceTest {

    @Mock
    private MultipartUploadCoreBridgeService multipartUploadCoreBridgeService;

    private MultipartPartUploadCommandService multipartPartUploadCommandService;

    @BeforeEach
    void setUp() {
        multipartPartUploadCommandService = new MultipartPartUploadCommandService(multipartUploadCoreBridgeService);
    }

    @Test
    @DisplayName("上传分片应委托给 core bridge")
    void uploadPart_shouldDelegateToCoreBridge() {
        byte[] data = "data".getBytes();
        when(multipartUploadCoreBridgeService.uploadPart("blog", "task-001", 1, data, "user-123"))
                .thenReturn("etag-1");

        String actual = multipartPartUploadCommandService.uploadPart("blog", "task-001", 1, data, "user-123");

        assertThat(actual).isEqualTo("etag-1");
        verify(multipartUploadCoreBridgeService).uploadPart("blog", "task-001", 1, data, "user-123");
    }
}
