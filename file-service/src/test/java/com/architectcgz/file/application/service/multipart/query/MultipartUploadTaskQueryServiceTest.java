package com.architectcgz.file.application.service.multipart.query;

import com.architectcgz.file.application.service.multipart.bridge.MultipartUploadCoreBridgeService;
import com.architectcgz.file.domain.model.UploadTask;
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
@DisplayName("MultipartUploadTaskQueryService 单元测试")
class MultipartUploadTaskQueryServiceTest {

    @Mock
    private MultipartUploadCoreBridgeService multipartUploadCoreBridgeService;

    private MultipartUploadTaskQueryService multipartUploadTaskQueryService;

    @BeforeEach
    void setUp() {
        multipartUploadTaskQueryService = new MultipartUploadTaskQueryService(multipartUploadCoreBridgeService);
    }

    @Test
    @DisplayName("查询分片上传任务列表应委托给 core bridge")
    void listTasks_shouldDelegateToCoreBridge() {
        List<UploadTask> tasks = List.of(UploadTask.builder().id("task-001").build());
        when(multipartUploadCoreBridgeService.listTasks("blog", "user-123")).thenReturn(tasks);

        List<UploadTask> actual = multipartUploadTaskQueryService.listTasks("blog", "user-123");

        assertThat(actual).isSameAs(tasks);
        verify(multipartUploadCoreBridgeService).listTasks("blog", "user-123");
    }
}
