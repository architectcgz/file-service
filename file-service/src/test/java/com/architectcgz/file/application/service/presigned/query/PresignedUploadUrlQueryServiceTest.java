package com.architectcgz.file.application.service.presigned.query;

import com.architectcgz.file.application.dto.PresignedUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadResponse;
import com.architectcgz.file.application.service.presigned.bridge.PresignedUploadCoreBridgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PresignedUploadUrlQueryService 单元测试")
class PresignedUploadUrlQueryServiceTest {

    @Mock
    private PresignedUploadCoreBridgeService presignedUploadCoreBridgeService;

    private PresignedUploadUrlQueryService presignedUploadUrlQueryService;

    @BeforeEach
    void setUp() {
        presignedUploadUrlQueryService = new PresignedUploadUrlQueryService(presignedUploadCoreBridgeService);
    }

    @Test
    @DisplayName("获取预签名上传地址应委托给 core bridge")
    void getPresignedUploadUrl_shouldDelegateToCoreBridge() {
        PresignedUploadRequest request = new PresignedUploadRequest();
        request.setFileName("avatar.png");
        request.setFileSize(512L);
        request.setContentType("image/png");
        request.setFileHash("hash-public");

        PresignedUploadResponse response = PresignedUploadResponse.builder()
                .presignedUrl("https://minio.example.com/presigned")
                .storagePath("blog/2026/03/14/user-1/uploads/avatar.png")
                .expiresAt(LocalDateTime.of(2026, 3, 14, 14, 30))
                .method("PUT")
                .headers(Map.of("Content-Type", "image/png"))
                .build();
        when(presignedUploadCoreBridgeService.getPresignedUploadUrl("blog", request, "user-1"))
                .thenReturn(response);

        PresignedUploadResponse actual =
                presignedUploadUrlQueryService.getPresignedUploadUrl("blog", request, "user-1");

        assertThat(actual).isSameAs(response);
        verify(presignedUploadCoreBridgeService).getPresignedUploadUrl("blog", request, "user-1");
    }
}
