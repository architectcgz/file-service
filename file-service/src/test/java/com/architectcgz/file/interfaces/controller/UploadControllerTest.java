package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.application.service.UploadApplicationService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.context.UserContext;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.config.WebMvcTestConfig;
import com.architectcgz.file.interfaces.dto.UploadResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UploadController.class, excludeAutoConfiguration = {
        MybatisAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(WebMvcTestConfig.class)
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UploadApplicationService uploadApplicationService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void testUploadImageUsesUserContext() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "image-data".getBytes()
        );

        UserContext.setUserId("context-user");
        when(uploadApplicationService.uploadImage(anyString(), any(), anyString()))
                .thenReturn(UploadResult.builder()
                        .fileId("file-001")
                        .url("https://cdn.example.com/file-001")
                        .originalFilename("avatar.jpg")
                        .size(10L)
                        .contentType(MediaType.IMAGE_JPEG_VALUE)
                        .build());

        mockMvc.perform(multipart("/api/v1/upload/image")
                        .file(file)
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileId").value("file-001"));

        verify(uploadApplicationService).uploadImage(eq("test-app"), any(), eq("context-user"));
    }

    @Test
    void testUploadFileWithoutIdentityReturnsForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "pdf-data".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/upload/file")
                        .file(file)
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.errorCode").value(FileServiceErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(uploadApplicationService);
    }

    @Test
    void testUploadImageWithoutAppIdReturnsMissingHeaderErrorCode() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "image-data".getBytes()
        );
        UserContext.setUserId("context-user");

        mockMvc.perform(multipart("/api/v1/upload/image")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value(FileServiceErrorCodes.MISSING_REQUEST_HEADER));

        verifyNoInteractions(uploadApplicationService);
    }

    @Test
    void testUploadFileReturnsBusinessErrorCode() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "pdf-data".getBytes()
        );
        UserContext.setUserId("context-user");
        when(uploadApplicationService.uploadFile(anyString(), any(), anyString()))
                .thenThrow(new BusinessException(
                        FileServiceErrorCodes.FILE_TOO_LARGE,
                        "文件过大"
                ));

        mockMvc.perform(multipart("/api/v1/upload/file")
                        .file(file)
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value(FileServiceErrorCodes.FILE_TOO_LARGE))
                .andExpect(jsonPath("$.message").value("文件过大"));
    }

    @Test
    void testDeleteFileUsesUserContext() throws Exception {
        UserContext.setUserId("context-user");

        mockMvc.perform(delete("/api/v1/upload/{fileId}", "file-001")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(uploadApplicationService).deleteFile("test-app", "file-001", "context-user");
    }
}
