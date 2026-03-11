package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.application.dto.FileDetailResponse;
import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.application.dto.UpdateAccessLevelRequest;
import com.architectcgz.file.application.service.FileAccessService;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.context.UserContext;
import com.architectcgz.file.config.WebMvcTestConfig;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileStatus;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FileController.class, excludeAutoConfiguration = {
        MybatisAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(WebMvcTestConfig.class)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FileAccessService fileAccessService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void testGetFileUrlUsesUserContext() throws Exception {
        UserContext.setUserId("context-user");
        when(fileAccessService.getFileUrl(anyString(), anyString(), anyString()))
                .thenReturn(FileUrlResponse.builder()
                        .url("https://cdn.example.com/file-001")
                        .permanent(true)
                        .build());

        mockMvc.perform(get("/api/v1/files/{fileId}/url", "file-001")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.url").value("https://cdn.example.com/file-001"))
                .andExpect(jsonPath("$.data.gatewayUrl").value("/api/v1/files/file-001/content"));

        verify(fileAccessService).getFileUrl("test-app", "file-001", "context-user");
    }

    @Test
    void testAccessFileContentRedirectsToResolvedUrl() throws Exception {
        UserContext.setUserId("context-user");
        when(fileAccessService.getFileUrl(eq("test-app"), eq("file-001"), eq("context-user")))
                .thenReturn(FileUrlResponse.builder()
                        .url("https://cdn.example.com/file-001")
                        .permanent(true)
                        .build());

        mockMvc.perform(get("/api/v1/files/{fileId}/content", "file-001")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://cdn.example.com/file-001"));

        verify(fileAccessService).getFileUrl("test-app", "file-001", "context-user");
    }

    @Test
    void testAccessPrivateFileContentAddsNoStoreHeader() throws Exception {
        UserContext.setUserId("context-user");
        when(fileAccessService.getFileUrl(eq("test-app"), eq("file-001"), eq("context-user")))
                .thenReturn(FileUrlResponse.builder()
                        .url("https://s3.example.com/presigned")
                        .permanent(false)
                        .build());

        mockMvc.perform(get("/api/v1/files/{fileId}/content", "file-001")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://s3.example.com/presigned"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void testAccessFileContentWithoutIdentityAllowsPublicFile() throws Exception {
        when(fileAccessService.getFileUrl(eq("test-app"), eq("file-001"), eq(null)))
                .thenReturn(FileUrlResponse.builder()
                        .url("https://cdn.example.com/public-file")
                        .permanent(true)
                        .build());

        mockMvc.perform(get("/api/v1/files/{fileId}/content", "file-001")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://cdn.example.com/public-file"));

        verify(fileAccessService).getFileUrl("test-app", "file-001", null);
    }

    @Test
    void testGetFileDetailWithoutIdentityAllowsPublicFile() throws Exception {
        when(fileAccessService.getFileDetail(eq("test-app"), eq("file-001"), eq(null)))
                .thenReturn(FileDetailResponse.builder()
                        .fileId("file-001")
                        .originalFilename("public.jpg")
                        .status(FileStatus.COMPLETED)
                        .accessLevel(AccessLevel.PUBLIC)
                        .createdAt(LocalDateTime.of(2026, 3, 10, 21, 0))
                        .updatedAt(LocalDateTime.of(2026, 3, 10, 21, 0))
                        .build());

        mockMvc.perform(get("/api/v1/files/{fileId}", "file-001")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileId").value("file-001"));

        verify(fileAccessService).getFileDetail("test-app", "file-001", null);
    }

    @Test
    void testAccessPrivateFileContentWithoutIdentityReturnsForbidden() throws Exception {
        when(fileAccessService.getFileUrl(eq("test-app"), eq("file-001"), eq(null)))
                .thenThrow(new AccessDeniedException("无权访问文件"));

        mockMvc.perform(get("/api/v1/files/{fileId}/content", "file-001")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void testUpdateAccessLevelUsesUserContext() throws Exception {
        UserContext.setUserId("context-user");
        UpdateAccessLevelRequest request = UpdateAccessLevelRequest.builder()
                .accessLevel(AccessLevel.PRIVATE)
                .build();

        mockMvc.perform(put("/api/v1/files/{fileId}/access-level", "file-001")
                        .contentType(APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(fileAccessService).updateAccessLevel("test-app", "file-001", "context-user", AccessLevel.PRIVATE);
    }

    @Test
    void testGetFileDetailUsesUserContext() throws Exception {
        UserContext.setUserId("context-user");
        when(fileAccessService.getFileDetail(anyString(), anyString(), anyString()))
                .thenReturn(FileDetailResponse.builder()
                        .fileId("file-001")
                        .userId("context-user")
                        .originalFilename("avatar.jpg")
                        .fileSize(1024L)
                        .contentType("image/jpeg")
                        .fileHash("hash-123")
                        .hashAlgorithm("MD5")
                        .status(FileStatus.COMPLETED)
                        .accessLevel(AccessLevel.PUBLIC)
                        .createdAt(LocalDateTime.of(2026, 3, 10, 21, 0))
                        .updatedAt(LocalDateTime.of(2026, 3, 10, 21, 0))
                        .build());

        mockMvc.perform(get("/api/v1/files/{fileId}", "file-001")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileId").value("file-001"))
                .andExpect(jsonPath("$.data.userId").value("context-user"));

        verify(fileAccessService).getFileDetail("test-app", "file-001", "context-user");
    }
}
