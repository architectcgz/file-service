package com.architectcgz.file.application.service;

import com.architectcgz.file.application.service.upload.command.FileDeleteCommandService;
import com.architectcgz.file.application.service.upload.command.FileUploadCommandService;
import com.architectcgz.file.application.service.upload.command.ImageUploadCommandService;
import com.architectcgz.file.interfaces.dto.UploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UploadApplicationService 门面单元测试")
class UploadApplicationServiceTest {

    @Mock
    private ImageUploadCommandService imageUploadCommandService;
    @Mock
    private FileUploadCommandService fileUploadCommandService;
    @Mock
    private FileDeleteCommandService fileDeleteCommandService;

    private UploadApplicationService uploadApplicationService;

    @BeforeEach
    void setUp() {
        uploadApplicationService = new UploadApplicationService(
                imageUploadCommandService,
                fileUploadCommandService,
                fileDeleteCommandService
        );
    }

    @Test
    @DisplayName("图片上传应委托给 image command service")
    void uploadImage_shouldDelegateToImageCommandService() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", "data".getBytes());
        UploadResult result = UploadResult.builder().fileId("file-001").build();
        when(imageUploadCommandService.uploadImage("blog", file, "user-123")).thenReturn(result);

        UploadResult actual = uploadApplicationService.uploadImage("blog", file, "user-123");

        assertThat(actual).isSameAs(result);
        verify(imageUploadCommandService).uploadImage("blog", file, "user-123");
    }

    @Test
    @DisplayName("文件上传应委托给 file command service")
    void uploadFile_shouldDelegateToFileCommandService() {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "data".getBytes());
        UploadResult result = UploadResult.builder().fileId("file-002").build();
        when(fileUploadCommandService.uploadFile("blog", file, "user-123")).thenReturn(result);

        UploadResult actual = uploadApplicationService.uploadFile("blog", file, "user-123");

        assertThat(actual).isSameAs(result);
        verify(fileUploadCommandService).uploadFile("blog", file, "user-123");
    }

    @Test
    @DisplayName("删除文件应委托给 delete command service")
    void deleteFile_shouldDelegateToDeleteCommandService() {
        uploadApplicationService.deleteFile("blog", "file-001", "user-123");

        verify(fileDeleteCommandService).deleteFile("blog", "file-001", "user-123");
    }
}
