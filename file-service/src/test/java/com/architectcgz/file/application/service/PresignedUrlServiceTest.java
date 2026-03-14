package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.ConfirmUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadResponse;
import com.architectcgz.file.application.service.presigned.command.PresignedUploadConfirmCommandService;
import com.architectcgz.file.application.service.presigned.query.PresignedUploadUrlQueryService;
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
@DisplayName("PresignedUrlService 门面单元测试")
class PresignedUrlServiceTest {

    @Mock
    private PresignedUploadUrlQueryService presignedUploadUrlQueryService;
    @Mock
    private PresignedUploadConfirmCommandService presignedUploadConfirmCommandService;

    private PresignedUrlService presignedUrlService;

    @BeforeEach
    void setUp() {
        presignedUrlService = new PresignedUrlService(
                presignedUploadUrlQueryService,
                presignedUploadConfirmCommandService
        );
    }

    @Test
    @DisplayName("获取预签名上传地址应委托给 query service")
    void getPresignedUploadUrl_shouldDelegateToQueryService() {
        PresignedUploadRequest request = new PresignedUploadRequest();
        PresignedUploadResponse response = PresignedUploadResponse.builder()
                .storagePath("blog/path/file.png")
                .build();
        when(presignedUploadUrlQueryService.getPresignedUploadUrl("blog", request, "user-1"))
                .thenReturn(response);

        PresignedUploadResponse actual = presignedUrlService.getPresignedUploadUrl("blog", request, "user-1");

        assertThat(actual).isSameAs(response);
        verify(presignedUploadUrlQueryService).getPresignedUploadUrl("blog", request, "user-1");
    }

    @Test
    @DisplayName("确认上传应委托给 command service")
    void confirmUpload_shouldDelegateToCommandService() {
        ConfirmUploadRequest request = new ConfirmUploadRequest();
        Map<String, String> result = Map.of("fileId", "file-001", "url", "https://cdn.example.com/file-001");
        when(presignedUploadConfirmCommandService.confirmUpload("blog", request, "user-1"))
                .thenReturn(result);

        Map<String, String> actual = presignedUrlService.confirmUpload("blog", request, "user-1");

        assertThat(actual).isSameAs(result);
        verify(presignedUploadConfirmCommandService).confirmUpload("blog", request, "user-1");
    }
}
