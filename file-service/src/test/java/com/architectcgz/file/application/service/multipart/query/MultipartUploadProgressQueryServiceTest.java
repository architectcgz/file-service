package com.architectcgz.file.application.service.multipart.query;

import com.architectcgz.file.application.dto.UploadProgressResponse;
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
@DisplayName("MultipartUploadProgressQueryService 单元测试")
class MultipartUploadProgressQueryServiceTest {

    @Mock
    private MultipartUploadCoreBridgeService multipartUploadCoreBridgeService;

    private MultipartUploadProgressQueryService multipartUploadProgressQueryService;

    @BeforeEach
    void setUp() {
        multipartUploadProgressQueryService = new MultipartUploadProgressQueryService(multipartUploadCoreBridgeService);
    }

    @Test
    @DisplayName("查询分片上传进度应委托给 core bridge")
    void getProgress_shouldDelegateToCoreBridge() {
        UploadProgressResponse response = UploadProgressResponse.builder()
                .taskId("task-001")
                .completedParts(2)
                .build();
        when(multipartUploadCoreBridgeService.getProgress("blog", "task-001", "user-123"))
                .thenReturn(response);

        UploadProgressResponse actual = multipartUploadProgressQueryService.getProgress("blog", "task-001", "user-123");

        assertThat(actual).isSameAs(response);
        verify(multipartUploadCoreBridgeService).getProgress("blog", "task-001", "user-123");
    }
}
