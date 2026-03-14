package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.DirectUploadCompleteRequest;
import com.architectcgz.file.application.dto.DirectUploadInitRequest;
import com.architectcgz.file.application.dto.DirectUploadInitResponse;
import com.architectcgz.file.application.dto.DirectUploadPartUrlRequest;
import com.architectcgz.file.application.dto.DirectUploadPartUrlResponse;
import com.architectcgz.file.application.dto.DirectUploadProgressResponse;
import com.architectcgz.file.application.service.direct.command.DirectUploadCompleteCommandService;
import com.architectcgz.file.application.service.direct.command.DirectUploadInitCommandService;
import com.architectcgz.file.application.service.direct.query.DirectUploadPartUrlQueryService;
import com.architectcgz.file.application.service.direct.query.DirectUploadProgressQueryService;
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
@DisplayName("DirectUploadService 门面单元测试")
class DirectUploadServiceTest {

    @Mock
    private DirectUploadInitCommandService directUploadInitCommandService;
    @Mock
    private DirectUploadProgressQueryService directUploadProgressQueryService;
    @Mock
    private DirectUploadPartUrlQueryService directUploadPartUrlQueryService;
    @Mock
    private DirectUploadCompleteCommandService directUploadCompleteCommandService;

    private DirectUploadService directUploadService;

    @BeforeEach
    void setUp() {
        directUploadService = new DirectUploadService(
                directUploadInitCommandService,
                directUploadProgressQueryService,
                directUploadPartUrlQueryService,
                directUploadCompleteCommandService
        );
    }

    @Test
    @DisplayName("初始化直传上传应委托给 init command service")
    void initDirectUpload_shouldDelegateToInitCommandService() {
        DirectUploadInitRequest request = new DirectUploadInitRequest();
        DirectUploadInitResponse response = DirectUploadInitResponse.builder()
                .taskId("task-001")
                .isInstantUpload(false)
                .build();
        when(directUploadInitCommandService.initDirectUpload("blog", request, "user-123"))
                .thenReturn(response);

        DirectUploadInitResponse actual = directUploadService.initDirectUpload("blog", request, "user-123");

        assertThat(actual).isSameAs(response);
        verify(directUploadInitCommandService).initDirectUpload("blog", request, "user-123");
    }

    @Test
    @DisplayName("查询上传进度应委托给 progress query service")
    void getUploadProgress_shouldDelegateToProgressQueryService() {
        DirectUploadProgressResponse response = DirectUploadProgressResponse.builder()
                .taskId("task-001")
                .completedPartNumbers(List.of(1, 2))
                .build();
        when(directUploadProgressQueryService.getUploadProgress("blog", "task-001", "user-123"))
                .thenReturn(response);

        DirectUploadProgressResponse actual = directUploadService.getUploadProgress("blog", "task-001", "user-123");

        assertThat(actual).isSameAs(response);
        verify(directUploadProgressQueryService).getUploadProgress("blog", "task-001", "user-123");
    }

    @Test
    @DisplayName("获取分片上传地址应委托给 part url query service")
    void getPartUploadUrls_shouldDelegateToPartUrlQueryService() {
        DirectUploadPartUrlRequest request = new DirectUploadPartUrlRequest();
        DirectUploadPartUrlResponse response = DirectUploadPartUrlResponse.builder()
                .taskId("task-001")
                .build();
        when(directUploadPartUrlQueryService.getPartUploadUrls("blog", request, "user-123"))
                .thenReturn(response);

        DirectUploadPartUrlResponse actual = directUploadService.getPartUploadUrls("blog", request, "user-123");

        assertThat(actual).isSameAs(response);
        verify(directUploadPartUrlQueryService).getPartUploadUrls("blog", request, "user-123");
    }

    @Test
    @DisplayName("完成直传上传应委托给 complete command service")
    void completeDirectUpload_shouldDelegateToCompleteCommandService() {
        DirectUploadCompleteRequest request = new DirectUploadCompleteRequest();
        when(directUploadCompleteCommandService.completeDirectUpload("blog", request, "user-123"))
                .thenReturn("file-001");

        String fileId = directUploadService.completeDirectUpload("blog", request, "user-123");

        assertThat(fileId).isEqualTo("file-001");
        verify(directUploadCompleteCommandService).completeDirectUpload("blog", request, "user-123");
    }
}
