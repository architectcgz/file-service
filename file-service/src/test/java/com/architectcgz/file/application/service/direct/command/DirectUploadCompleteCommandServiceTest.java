package com.architectcgz.file.application.service.direct.command;

import com.architectcgz.file.application.dto.DirectUploadCompleteRequest;
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
@DisplayName("DirectUploadCompleteCommandService 单元测试")
class DirectUploadCompleteCommandServiceTest {

    @Mock
    private DirectUploadCoreBridgeService directUploadCoreBridgeService;

    private DirectUploadCompleteCommandService directUploadCompleteCommandService;

    @BeforeEach
    void setUp() {
        directUploadCompleteCommandService = new DirectUploadCompleteCommandService(directUploadCoreBridgeService);
    }

    @Test
    @DisplayName("完成直传上传应委托给 core bridge")
    void completeDirectUpload_shouldDelegateToCoreBridge() {
        DirectUploadCompleteRequest request = new DirectUploadCompleteRequest();
        request.setTaskId("task-001");
        request.setContentType("application/pdf");
        request.setParts(List.of());

        when(directUploadCoreBridgeService.completeDirectUpload("blog", request, "user-123"))
                .thenReturn("file-001");

        String fileId = directUploadCompleteCommandService.completeDirectUpload("blog", request, "user-123");

        assertThat(fileId).isEqualTo("file-001");
        verify(directUploadCoreBridgeService).completeDirectUpload("blog", request, "user-123");
    }
}
