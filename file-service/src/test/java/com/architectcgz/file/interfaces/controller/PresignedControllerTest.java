package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.application.dto.ConfirmUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadResponse;
import com.architectcgz.file.application.service.PresignedUrlService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.context.UserContext;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.config.WebMvcTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PresignedController.class, excludeAutoConfiguration = {
        MybatisAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(WebMvcTestConfig.class)
class PresignedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PresignedUrlService presignedUrlService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void testGetPresignedUploadUrl() throws Exception {
        PresignedUploadRequest request = new PresignedUploadRequest();
        request.setFileName("image.jpg");
        request.setFileSize(1024L);
        request.setContentType("image/jpeg");
        request.setFileHash("hash-123");

        PresignedUploadResponse response = PresignedUploadResponse.builder()
                .presignedUrl("https://s3.example.com/upload")
                .storagePath("test-app/2026/03/10/user-1/image.jpg")
                .expiresAt(LocalDateTime.of(2026, 3, 10, 22, 0))
                .method("PUT")
                .headers(Map.of("Content-Type", "image/jpeg"))
                .build();

        when(presignedUrlService.getPresignedUploadUrl(anyString(), any(PresignedUploadRequest.class), anyString()))
                .thenReturn(response);
        UserContext.setUserId("user-1");

        mockMvc.perform(post("/api/v1/upload/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.presignedUrl").value("https://s3.example.com/upload"))
                .andExpect(jsonPath("$.data.storagePath").value("test-app/2026/03/10/user-1/image.jpg"))
                .andExpect(jsonPath("$.data.method").value("PUT"));
    }

    @Test
    void testGetPresignedUploadUrlUsesUserContextBeforeHeader() throws Exception {
        PresignedUploadRequest request = new PresignedUploadRequest();
        request.setFileName("image.jpg");
        request.setFileSize(1024L);
        request.setContentType("image/jpeg");
        request.setFileHash("hash-123");

        UserContext.setUserId("context-user");
        when(presignedUrlService.getPresignedUploadUrl(anyString(), any(PresignedUploadRequest.class), anyString()))
                .thenReturn(PresignedUploadResponse.builder()
                        .presignedUrl("https://s3.example.com/upload")
                        .storagePath("test-app/2026/03/10/context-user/image.jpg")
                        .expiresAt(LocalDateTime.of(2026, 3, 10, 22, 0))
                        .method("PUT")
                        .headers(Map.of())
                        .build());

        mockMvc.perform(post("/api/v1/upload/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(presignedUrlService)
                .getPresignedUploadUrl(anyString(), any(PresignedUploadRequest.class), eq("context-user"));
    }

    @Test
    void testGetPresignedUploadUrlWithoutIdentityReturnsForbidden() throws Exception {
        PresignedUploadRequest request = new PresignedUploadRequest();
        request.setFileName("image.jpg");
        request.setFileSize(1024L);
        request.setContentType("image/jpeg");
        request.setFileHash("hash-123");

        mockMvc.perform(post("/api/v1/upload/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.errorCode").value(FileServiceErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(presignedUrlService);
    }

    @Test
    void testConfirmUploadUsesUserContext() throws Exception {
        ConfirmUploadRequest request = new ConfirmUploadRequest();
        request.setAppId("test-app");
        request.setStoragePath("test-app/2026/03/10/context-user/image.jpg");
        request.setFileHash("hash-123");
        request.setOriginalFilename("image.jpg");

        UserContext.setUserId("context-user");
        when(presignedUrlService.confirmUpload(anyString(), any(ConfirmUploadRequest.class), anyString()))
                .thenReturn(Map.of(
                        "fileId", "file-001",
                        "url", "https://cdn.example.com/files/file-001"
                ));

        mockMvc.perform(post("/api/v1/upload/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileId").value("file-001"))
                .andExpect(jsonPath("$.data.url").value("https://cdn.example.com/files/file-001"));

        verify(presignedUrlService)
                .confirmUpload(anyString(), any(ConfirmUploadRequest.class), eq("context-user"));
    }

    @Test
    void testConfirmUploadWithoutIdentityReturnsForbidden() throws Exception {
        ConfirmUploadRequest request = new ConfirmUploadRequest();
        request.setAppId("test-app");
        request.setStoragePath("test-app/2026/03/10/user-1/image.jpg");
        request.setFileHash("hash-123");
        request.setOriginalFilename("image.jpg");

        mockMvc.perform(post("/api/v1/upload/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.errorCode").value(FileServiceErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(presignedUrlService);
    }

    @Test
    void testGetPresignedUploadUrlValidationErrorReturnsErrorCode() throws Exception {
        PresignedUploadRequest request = new PresignedUploadRequest();
        request.setFileName("");
        request.setFileSize(1024L);
        request.setContentType("image/jpeg");
        request.setFileHash("hash-123");
        UserContext.setUserId("context-user");

        mockMvc.perform(post("/api/v1/upload/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value(FileServiceErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("文件名不能为空")));

        verifyNoInteractions(presignedUrlService);
    }

    @Test
    void testGetPresignedUploadUrlReturnsBusinessErrorCode() throws Exception {
        PresignedUploadRequest request = new PresignedUploadRequest();
        request.setFileName("image.jpg");
        request.setFileSize(1024L);
        request.setContentType("image/jpeg");
        request.setFileHash("hash-123");
        request.setAccessLevel("unsupported");

        UserContext.setUserId("context-user");
        when(presignedUrlService.getPresignedUploadUrl(anyString(), any(PresignedUploadRequest.class), anyString()))
                .thenThrow(new BusinessException(
                        FileServiceErrorCodes.UNSUPPORTED_ACCESS_LEVEL,
                        "不支持的访问级别: unsupported"
                ));

        mockMvc.perform(post("/api/v1/upload/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value(FileServiceErrorCodes.UNSUPPORTED_ACCESS_LEVEL))
                .andExpect(jsonPath("$.message").value("不支持的访问级别: unsupported"));
    }
}
