package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.dto.InitUploadRequest;
import com.architectcgz.file.application.dto.InitUploadResponse;
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
@DisplayName("MultipartUploadInitCommandService 单元测试")
class MultipartUploadInitCommandServiceTest {

    @Mock
    private MultipartUploadCoreBridgeService multipartUploadCoreBridgeService;

    private MultipartUploadInitCommandService multipartUploadInitCommandService;

    @BeforeEach
    void setUp() {
        multipartUploadInitCommandService = new MultipartUploadInitCommandService(multipartUploadCoreBridgeService);
    }

    @Test
    @DisplayName("初始化分片上传应委托给 core bridge")
    void initUpload_shouldDelegateToCoreBridge() {
        InitUploadRequest request = new InitUploadRequest();
        request.setFileName("archive.zip");
        request.setFileSize(2048L);
        request.setContentType("application/zip");
        request.setFileHash("hash-456");

        InitUploadResponse response = InitUploadResponse.builder()
                .taskId("task-001")
                .uploadId("upload-001")
                .chunkSize(1024)
                .totalParts(2)
                .build();
        when(multipartUploadCoreBridgeService.initUpload("blog", request, "user-123"))
                .thenReturn(response);

        InitUploadResponse actual = multipartUploadInitCommandService.initUpload("blog", request, "user-123");

        assertThat(actual).isSameAs(response);
        verify(multipartUploadCoreBridgeService).initUpload("blog", request, "user-123");
    }
}
