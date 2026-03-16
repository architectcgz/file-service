package com.architectcgz.file.application.service.direct.query;

import com.architectcgz.file.application.dto.DirectUploadProgressResponse;
import com.architectcgz.file.application.service.direct.bridge.DirectUploadCoreBridgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DirectUploadProgressQueryService 单元测试")
class DirectUploadProgressQueryServiceTest {

    @Mock
    private DirectUploadCoreBridgeService directUploadCoreBridgeService;

    private DirectUploadProgressQueryService directUploadProgressQueryService;

    @BeforeEach
    void setUp() {
        directUploadProgressQueryService = new DirectUploadProgressQueryService(directUploadCoreBridgeService);
    }

    @Test
    @DisplayName("查询直传进度应委托给 core bridge")
    void getUploadProgress_shouldDelegateToCoreBridge() {
        DirectUploadProgressResponse response = DirectUploadProgressResponse.builder()
                .taskId("task-001")
                .completedPartNumbers(List.of(1, 3))
                .build();
        when(directUploadCoreBridgeService.getUploadProgress("blog", "task-001", "user-123"))
                .thenReturn(response);

        DirectUploadProgressResponse actual =
                directUploadProgressQueryService.getUploadProgress("blog", "task-001", "user-123");

        assertThat(actual).isSameAs(response);
        verify(directUploadCoreBridgeService).getUploadProgress("blog", "task-001", "user-123");
    }
}
