package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.InitUploadRequest;
import com.architectcgz.file.application.dto.InitUploadResponse;
import com.architectcgz.file.application.dto.UploadProgressResponse;
import com.architectcgz.file.application.service.multipart.command.MultipartPartUploadCommandService;
import com.architectcgz.file.application.service.multipart.command.MultipartUploadAbortCommandService;
import com.architectcgz.file.application.service.multipart.command.MultipartUploadCompleteCommandService;
import com.architectcgz.file.application.service.multipart.command.MultipartUploadInitCommandService;
import com.architectcgz.file.application.service.multipart.query.MultipartUploadProgressQueryService;
import com.architectcgz.file.application.service.multipart.query.MultipartUploadTaskQueryService;
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
@DisplayName("MultipartUploadService 门面单元测试")
class MultipartUploadServiceTest {

    @Mock
    private MultipartUploadInitCommandService multipartUploadInitCommandService;
    @Mock
    private MultipartPartUploadCommandService multipartPartUploadCommandService;
    @Mock
    private MultipartUploadCompleteCommandService multipartUploadCompleteCommandService;
    @Mock
    private MultipartUploadAbortCommandService multipartUploadAbortCommandService;
    @Mock
    private MultipartUploadProgressQueryService multipartUploadProgressQueryService;
    @Mock
    private MultipartUploadTaskQueryService multipartUploadTaskQueryService;

    private MultipartUploadService multipartUploadService;

    @BeforeEach
    void setUp() {
        multipartUploadService = new MultipartUploadService(
                multipartUploadInitCommandService,
                multipartPartUploadCommandService,
                multipartUploadCompleteCommandService,
                multipartUploadAbortCommandService,
                multipartUploadProgressQueryService,
                multipartUploadTaskQueryService
        );
    }

    @Test
    @DisplayName("初始化分片上传应委托给 init command service")
    void initUpload_shouldDelegateToInitCommandService() {
        InitUploadRequest request = new InitUploadRequest();
        InitUploadResponse response = InitUploadResponse.builder()
                .taskId("task-001")
                .build();
        when(multipartUploadInitCommandService.initUpload("blog", request, "user-123"))
                .thenReturn(response);

        InitUploadResponse actual = multipartUploadService.initUpload("blog", request, "user-123");

        assertThat(actual).isSameAs(response);
        verify(multipartUploadInitCommandService).initUpload("blog", request, "user-123");
    }

    @Test
    @DisplayName("上传分片应委托给 part upload command service")
    void uploadPart_shouldDelegateToPartUploadCommandService() {
        byte[] data = "data".getBytes();
        when(multipartPartUploadCommandService.uploadPart("blog", "task-001", 1, data, "user-123"))
                .thenReturn("etag-1");

        String etag = multipartUploadService.uploadPart("blog", "task-001", 1, data, "user-123");

        assertThat(etag).isEqualTo("etag-1");
        verify(multipartPartUploadCommandService).uploadPart("blog", "task-001", 1, data, "user-123");
    }

    @Test
    @DisplayName("完成上传应委托给 complete command service")
    void completeUpload_shouldDelegateToCompleteCommandService() {
        when(multipartUploadCompleteCommandService.completeUpload("blog", "task-001", "user-123"))
                .thenReturn("file-001");

        String fileId = multipartUploadService.completeUpload("blog", "task-001", "user-123");

        assertThat(fileId).isEqualTo("file-001");
        verify(multipartUploadCompleteCommandService).completeUpload("blog", "task-001", "user-123");
    }

    @Test
    @DisplayName("中止上传应委托给 abort command service")
    void abortUpload_shouldDelegateToAbortCommandService() {
        multipartUploadService.abortUpload("blog", "task-001", "user-123");

        verify(multipartUploadAbortCommandService).abortUpload("blog", "task-001", "user-123");
    }

    @Test
    @DisplayName("查询进度应委托给 progress query service")
    void getProgress_shouldDelegateToProgressQueryService() {
        UploadProgressResponse response = UploadProgressResponse.builder()
                .taskId("task-001")
                .completedParts(1)
                .build();
        when(multipartUploadProgressQueryService.getProgress("blog", "task-001", "user-123"))
                .thenReturn(response);

        UploadProgressResponse actual = multipartUploadService.getProgress("blog", "task-001", "user-123");

        assertThat(actual).isSameAs(response);
        verify(multipartUploadProgressQueryService).getProgress("blog", "task-001", "user-123");
    }

    @Test
    @DisplayName("列出任务应委托给 task query service")
    void listTasks_shouldDelegateToTaskQueryService() {
        List<UploadTask> tasks = List.of(UploadTask.builder().id("task-001").build());
        when(multipartUploadTaskQueryService.listTasks("blog", "user-123")).thenReturn(tasks);

        List<UploadTask> actual = multipartUploadService.listTasks("blog", "user-123");

        assertThat(actual).isSameAs(tasks);
        verify(multipartUploadTaskQueryService).listTasks("blog", "user-123");
    }
}
