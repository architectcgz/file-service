package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.application.dto.InitUploadRequest;
import com.architectcgz.file.application.dto.InitUploadResponse;
import com.architectcgz.file.application.service.FileAccessService;
import com.architectcgz.file.application.service.MultipartUploadService;
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

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Multipart upload controller test
 */
@WebMvcTest(controllers = MultipartController.class, excludeAutoConfiguration = {
    MybatisAutoConfiguration.class,
    DataSourceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(WebMvcTestConfig.class)
class MultipartControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private MultipartUploadService multipartUploadService;

    @MockBean
    private FileAccessService fileAccessService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }
    
    @Test
    void testInitUpload() throws Exception {
        // 准备测试数据
        InitUploadRequest request = new InitUploadRequest();
        request.setFileName("test-video.mp4");
        request.setFileSize(104857600L); // 100MB
        request.setFileHash("d41d8cd98f00b204e9800998ecf8427e");
        request.setContentType("video/mp4");
        
        InitUploadResponse response = InitUploadResponse.builder()
                .taskId("test-task-id")
                .uploadId("test-upload-id")
                .chunkSize(5242880)
                .totalParts(20)
                .completedParts(new ArrayList<>())
                .build();
        
        when(multipartUploadService.initUpload(anyString(), any(InitUploadRequest.class), anyString()))
                .thenReturn(response);
        UserContext.setUserId("1");
        
        // 执行测试
        mockMvc.perform(post("/api/v1/multipart/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value("test-task-id"))
                .andExpect(jsonPath("$.data.uploadId").value("test-upload-id"))
                .andExpect(jsonPath("$.data.chunkSize").value(5242880))
                .andExpect(jsonPath("$.data.totalParts").value(20));
    }
    
    @Test
    void testUploadPart() throws Exception {
        // 准备测试数据
        String taskId = "test-task-id";
        int partNumber = 1;
        byte[] data = "test data".getBytes();
        String etag = "test-etag-123";
        
        when(multipartUploadService.uploadPart(eq("test-app"), eq(taskId), eq(partNumber), any(byte[].class), anyString()))
                .thenReturn(etag);
        UserContext.setUserId("1");
        
        // 执行测试 - send raw bytes as request body
        mockMvc.perform(put("/api/v1/multipart/{taskId}/parts/{partNumber}", taskId, partNumber)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(data)
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(etag));
    }
    
    @Test
    void testUploadPartWithInvalidTaskId() throws Exception {
        // 准备测试数据
        String taskId = "invalid-task-id";
        int partNumber = 1;
        byte[] data = "test data".getBytes();
        
        when(multipartUploadService.uploadPart(eq("test-app"), eq(taskId), eq(partNumber), any(byte[].class), anyString()))
                .thenThrow(new BusinessException("UPLOAD_TASK_NOT_FOUND", "Upload task not found"));
        UserContext.setUserId("1");
        
        // Execute test - should return error
        mockMvc.perform(put("/api/v1/multipart/{taskId}/parts/{partNumber}", taskId, partNumber)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(data)
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void testInitUploadUsesUserContextBeforeHeader() throws Exception {
        InitUploadRequest request = new InitUploadRequest();
        request.setFileName("test-video.mp4");
        request.setFileSize(104857600L);
        request.setFileHash("d41d8cd98f00b204e9800998ecf8427e");
        request.setContentType("video/mp4");

        InitUploadResponse response = InitUploadResponse.builder()
                .taskId("test-task-id")
                .uploadId("test-upload-id")
                .chunkSize(5242880)
                .totalParts(20)
                .completedParts(new ArrayList<>())
                .build();

        UserContext.setUserId("context-user");
        when(multipartUploadService.initUpload(anyString(), any(InitUploadRequest.class), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/multipart/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(multipartUploadService).initUpload(anyString(), any(InitUploadRequest.class), eq("context-user"));
    }

    @Test
    void testInitUploadWithoutIdentityReturnsForbidden() throws Exception {
        InitUploadRequest request = new InitUploadRequest();
        request.setFileName("test-video.mp4");
        request.setFileSize(104857600L);
        request.setFileHash("d41d8cd98f00b204e9800998ecf8427e");
        request.setContentType("video/mp4");

        mockMvc.perform(post("/api/v1/multipart/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(multipartUploadService);
    }

    @Test
    void testCompleteUploadReturnsResolvedFileUrl() throws Exception {
        when(multipartUploadService.completeUpload("test-app", "task-001", "1")).thenReturn("file-001");
        when(fileAccessService.getFileUrl("test-app", "file-001", "1"))
                .thenReturn(FileUrlResponse.builder()
                        .url("https://cdn.example.com/files/file-001")
                        .permanent(true)
                        .build());
        UserContext.setUserId("1");

        mockMvc.perform(post("/api/v1/multipart/{taskId}/complete", "task-001")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileId").value("file-001"))
                .andExpect(jsonPath("$.data.url").value("https://cdn.example.com/files/file-001"));
    }
}
