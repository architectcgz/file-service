package com.architectcgz.file.application.service.presigned.command;

import com.architectcgz.file.application.dto.ConfirmUploadRequest;
import com.architectcgz.file.application.service.presigned.bridge.PresignedUploadCoreBridgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PresignedUploadConfirmCommandService 单元测试")
class PresignedUploadConfirmCommandServiceTest {

    @Mock
    private PresignedUploadCoreBridgeService presignedUploadCoreBridgeService;

    private PresignedUploadConfirmCommandService presignedUploadConfirmCommandService;

    @BeforeEach
    void setUp() {
        presignedUploadConfirmCommandService = new PresignedUploadConfirmCommandService(presignedUploadCoreBridgeService);
    }

    @Test
    @DisplayName("确认上传应委托给 core bridge")
    void confirmUpload_shouldDelegateToCoreBridge() {
        ConfirmUploadRequest request = new ConfirmUploadRequest();
        request.setStoragePath("blog/private/object.png");
        request.setFileHash("hash-private");
        request.setOriginalFilename("object.png");
        request.setAccessLevel("private");

        Map<String, String> result = Map.of(
                "fileId", "file-001",
                "url", "https://minio.example.com/private-signed"
        );
        when(presignedUploadCoreBridgeService.confirmUpload("blog", request, "user-1"))
                .thenReturn(result);

        Map<String, String> actual = presignedUploadConfirmCommandService.confirmUpload("blog", request, "user-1");

        assertThat(actual).isSameAs(result);
        verify(presignedUploadCoreBridgeService).confirmUpload("blog", request, "user-1");
    }
}
