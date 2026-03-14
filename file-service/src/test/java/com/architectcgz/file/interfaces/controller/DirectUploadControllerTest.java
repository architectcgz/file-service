package com.architectcgz.file.interfaces.controller;

import com.architectcgz.file.application.dto.DirectUploadCompleteRequest;
import com.architectcgz.file.application.dto.DirectUploadInitRequest;
import com.architectcgz.file.application.dto.DirectUploadInitResponse;
import com.architectcgz.file.application.dto.DirectUploadPartUrlRequest;
import com.architectcgz.file.application.dto.DirectUploadPartUrlResponse;
import com.architectcgz.file.application.dto.DirectUploadProgressResponse;
import com.architectcgz.file.application.service.DirectUploadService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DirectUploadController.class, excludeAutoConfiguration = {
        MybatisAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(WebMvcTestConfig.class)
class DirectUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DirectUploadService directUploadService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void testInitUploadUsesUserContext() throws Exception {
        DirectUploadInitRequest request = new DirectUploadInitRequest();
        request.setFileName("archive.zip");
        request.setFileSize(2048L);
        request.setContentType("application/zip");
        request.setFileHash("hash-123");

        when(directUploadService.initDirectUpload(anyString(), any(DirectUploadInitRequest.class), anyString()))
                .thenReturn(DirectUploadInitResponse.builder()
                        .taskId("task-001")
                        .uploadId("upload-001")
                        .storagePath("test-app/2026/03/10/context-user/archive.zip")
                        .chunkSize(5242880)
                        .totalParts(1)
                        .build());
        UserContext.setUserId("context-user");

        mockMvc.perform(post("/api/v1/direct-upload/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value("task-001"));

        verify(directUploadService)
                .initDirectUpload(eq("test-app"), any(DirectUploadInitRequest.class), eq("context-user"));
    }

    @Test
    void testGetPartUploadUrlsWithoutIdentityReturnsForbidden() throws Exception {
        DirectUploadPartUrlRequest request = new DirectUploadPartUrlRequest();
        request.setTaskId("task-001");
        request.setPartNumbers(List.of(1, 2));

        mockMvc.perform(post("/api/v1/direct-upload/part-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.errorCode").value(FileServiceErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(directUploadService);
    }

    @Test
    void testInitUploadValidationErrorReturnsErrorCode() throws Exception {
        DirectUploadInitRequest request = new DirectUploadInitRequest();
        request.setFileName("archive.zip");
        request.setFileSize(2048L);
        request.setContentType("application/zip");
        request.setFileHash("");
        UserContext.setUserId("context-user");

        mockMvc.perform(post("/api/v1/direct-upload/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value(FileServiceErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("文件哈希不能为空")));

        verifyNoInteractions(directUploadService);
    }

    @Test
    void testCompleteUploadUsesUserContext() throws Exception {
        DirectUploadCompleteRequest request = new DirectUploadCompleteRequest();
        request.setTaskId("task-001");
        request.setContentType("application/zip");
        DirectUploadCompleteRequest.PartInfo part = new DirectUploadCompleteRequest.PartInfo();
        part.setPartNumber(1);
        part.setEtag("etag-1");
        request.setParts(List.of(part));

        UserContext.setUserId("context-user");
        when(directUploadService.completeDirectUpload(anyString(), any(DirectUploadCompleteRequest.class), anyString()))
                .thenReturn("file-001");

        mockMvc.perform(post("/api/v1/direct-upload/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("file-001"));

        verify(directUploadService).completeDirectUpload(eq("test-app"), any(DirectUploadCompleteRequest.class), eq("context-user"));
    }

    @Test
    void testGetUploadProgressUsesUserContext() throws Exception {
        UserContext.setUserId("context-user");
        when(directUploadService.getUploadProgress(anyString(), anyString(), anyString()))
                .thenReturn(DirectUploadProgressResponse.builder()
                        .taskId("task-001")
                        .totalParts(4)
                        .completedParts(2)
                        .uploadedBytes(10L)
                        .totalBytes(20L)
                        .percentage(50)
                        .build());

        mockMvc.perform(get("/api/v1/direct-upload/task-001/progress")
                        .header("X-App-Id", "test-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value("task-001"))
                .andExpect(jsonPath("$.data.percentage").value(50));

        verify(directUploadService).getUploadProgress("test-app", "task-001", "context-user");
    }

    @Test
    void testCompleteUploadReturnsBusinessErrorCode() throws Exception {
        DirectUploadCompleteRequest request = new DirectUploadCompleteRequest();
        request.setTaskId("task-001");
        request.setContentType("application/zip");
        request.setParts(List.of());

        UserContext.setUserId("context-user");
        when(directUploadService.completeDirectUpload(anyString(), any(DirectUploadCompleteRequest.class), anyString()))
                .thenThrow(new BusinessException(
                        FileServiceErrorCodes.PART_ETAG_MISMATCH,
                        "分片ETag不匹配: 1"
                ));

        mockMvc.perform(post("/api/v1/direct-upload/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-App-Id", "test-app")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value(FileServiceErrorCodes.PART_ETAG_MISMATCH))
                .andExpect(jsonPath("$.message").value("分片ETag不匹配: 1"));
    }
}
