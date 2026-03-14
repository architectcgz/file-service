package com.architectcgz.file.integration;

import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.result.ApiResponse;
import com.architectcgz.file.config.TestStorageConfig;
import com.architectcgz.file.interfaces.dto.UploadResult;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Multi-App File Isolation Integration Test
 * 
 * Tests:
 * - Blog app uploads file, IM app cannot access
 * - Same file in different appIds are independently deduplicated
 * - File deletion validates appId ownership
 * 
 * Validates: Requirements 2.4, 2.5, 4.4, 4.5, 4.6
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestStorageConfig.class)
@TestPropertySource(properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.config.import=",
    "storage.type=local",
    "storage.local.base-path=./test-uploads",
    "storage.access.private-url-expire-seconds=3600",
    "storage.access.presigned-url-expire-seconds=900",
    "upload.validation.enabled=false"
})
@Transactional
@DisplayName("Multi-App File Isolation Integration Test")
class MultiAppFileIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileRecordRepository fileRecordRepository;

    @Autowired
    private StorageObjectRepository storageObjectRepository;

    private MockMultipartFile testImageFile;
    private static final String BLOG_APP_ID = "blog";
    private static final String IM_APP_ID = "im";
    private static final String BLOG_USER_ID = "12345";
    private static final String IM_USER_ID = "67890";

    @BeforeEach
    void setUp() {
        // Create a test image file
        byte[] imageContent = "fake-image-content-for-testing".getBytes(StandardCharsets.UTF_8);
        testImageFile = new MockMultipartFile(
            "file",
            "test-image.jpg",
            "image/jpeg",
            imageContent
        );
    }

    @Test
    @DisplayName("Blog app uploads file, IM app cannot access - should return 404")
    void blogUpload_imAccess_shouldReturn404() throws Exception {
        // 1. Blog app uploads a file
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/upload/image")
                .file(testImageFile)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(BLOG_USER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.fileId").exists())
            .andReturn();

        String responseBody = uploadResult.getResponse().getContentAsString();
        ApiResponse<UploadResult> response = objectMapper.readValue(
            responseBody,
            objectMapper.getTypeFactory().constructParametricType(
                ApiResponse.class,
                UploadResult.class
            )
        );
        
        String blogFileId = response.getData().getFileId();
        assertThat(blogFileId).isNotNull();

        // 2. Verify file was created with blog appId
        FileRecord fileRecord = fileRecordRepository.findById(blogFileId).orElse(null);
        assertThat(fileRecord).isNotNull();
        assertThat(fileRecord.getAppId()).isEqualTo(BLOG_APP_ID);
        assertThat(fileRecord.getUserId()).isEqualTo(BLOG_USER_ID);

        // 3. IM app tries to access the blog file - should fail with 404 to avoid leaking existence
        mockMvc.perform(get("/api/v1/files/" + blogFileId + "/url")
                .header("X-App-Id", IM_APP_ID)
                .header("X-User-Id", String.valueOf(IM_USER_ID)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value(String.format(FileServiceErrorMessages.FILE_NOT_FOUND_WITH_PATH, blogFileId)));

        // 4. Blog app can access its own file - should succeed
        mockMvc.perform(get("/api/v1/files/" + blogFileId + "/url")
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(BLOG_USER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.url").exists());
    }

    @Test
    @DisplayName("Same file in different appIds should be independently deduplicated")
    void sameFile_differentAppIds_shouldBeIndependent() throws Exception {
        // 1. Blog app uploads file
        MvcResult blogUpload1 = mockMvc.perform(multipart("/api/v1/upload/image")
                .file(testImageFile)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(BLOG_USER_ID)))
            .andExpect(status().isOk())
            .andReturn();

        String blogFileId1 = extractFileId(blogUpload1);

        // 2. Blog app uploads same file again (should deduplicate within blog)
        MockMultipartFile sameFile1 = new MockMultipartFile(
            "file",
            "test-image-2.jpg",
            "image/jpeg",
            testImageFile.getBytes()
        );

        MvcResult blogUpload2 = mockMvc.perform(multipart("/api/v1/upload/image")
                .file(sameFile1)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(BLOG_USER_ID)))
            .andExpect(status().isOk())
            .andReturn();

        String blogFileId2 = extractFileId(blogUpload2);

        // 3. Verify blog files share same storage object
        FileRecord blogRecord1 = fileRecordRepository.findById(blogFileId1).orElse(null);
        FileRecord blogRecord2 = fileRecordRepository.findById(blogFileId2).orElse(null);
        
        assertThat(blogRecord1).isNotNull();
        assertThat(blogRecord2).isNotNull();
        assertThat(blogRecord1.getStorageObjectId())
            .isEqualTo(blogRecord2.getStorageObjectId());

        // 4. IM app uploads same file (should create separate storage object)
        MockMultipartFile sameFile2 = new MockMultipartFile(
            "file",
            "test-image-3.jpg",
            "image/jpeg",
            testImageFile.getBytes()
        );

        MvcResult imUpload = mockMvc.perform(multipart("/api/v1/upload/image")
                .file(sameFile2)
                .header("X-App-Id", IM_APP_ID)
                .header("X-User-Id", String.valueOf(IM_USER_ID)))
            .andExpect(status().isOk())
            .andReturn();

        String imFileId = extractFileId(imUpload);

        // 5. Verify IM file has different storage object (appId isolation)
        FileRecord imRecord = fileRecordRepository.findById(imFileId).orElse(null);
        
        assertThat(imRecord).isNotNull();
        assertThat(imRecord.getAppId()).isEqualTo(IM_APP_ID);
        assertThat(imRecord.getStorageObjectId())
            .isNotEqualTo(blogRecord1.getStorageObjectId());

        // 6. Verify storage paths are retrieved from storage objects (not directly from file records)
        // FileRecord.storagePath is null, actual path is in StorageObject
        assertThat(blogRecord1.getStorageObjectId()).isNotNull();
        assertThat(imRecord.getStorageObjectId()).isNotNull();
    }

    @Test
    @DisplayName("File deletion should validate appId ownership")
    void deleteFile_shouldValidateAppIdOwnership() throws Exception {
        // 1. Blog app uploads a file
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/upload/image")
                .file(testImageFile)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(BLOG_USER_ID)))
            .andExpect(status().isOk())
            .andReturn();

        String blogFileId = extractFileId(uploadResult);

        // 2. IM app tries to delete blog file - should fail with 403
        mockMvc.perform(delete("/api/v1/upload/" + blogFileId)
                .header("X-App-Id", IM_APP_ID)
                .header("X-User-Id", String.valueOf(IM_USER_ID)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("文件不属于该应用"));

        // 3. Verify file still exists
        FileRecord fileRecord = fileRecordRepository.findById(blogFileId).orElse(null);
        assertThat(fileRecord).isNotNull();
        assertThat(fileRecord.getStatus().toString()).isNotEqualTo("DELETED");

        // 4. Blog app can delete its own file - should succeed
        mockMvc.perform(delete("/api/v1/upload/" + blogFileId)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(BLOG_USER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        // 5. Verify file is marked as deleted
        FileRecord deletedRecord = fileRecordRepository.findById(blogFileId).orElse(null);
        assertThat(deletedRecord).isNotNull();
        assertThat(deletedRecord.getStatus().toString()).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("Cross-appId file detail access should return 404")
    void getFileDetail_crossAppId_shouldReturn404() throws Exception {
        // 1. Blog app uploads a file
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/upload/image")
                .file(testImageFile)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(BLOG_USER_ID)))
            .andExpect(status().isOk())
            .andReturn();

        String blogFileId = extractFileId(uploadResult);

        // 2. IM app tries to get file details - should fail with 404 to avoid leaking existence
        mockMvc.perform(get("/api/v1/files/" + blogFileId)
                .header("X-App-Id", IM_APP_ID)
                .header("X-User-Id", String.valueOf(IM_USER_ID)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value(String.format(FileServiceErrorMessages.FILE_NOT_FOUND_WITH_PATH, blogFileId)));

        // 3. Blog app can get its own file details - should succeed
        mockMvc.perform(get("/api/v1/files/" + blogFileId)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(BLOG_USER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.fileId").value(blogFileId))
            .andExpect(jsonPath("$.data.originalFilename").exists());
    }

    /**
     * Helper method to extract fileId from upload response
     */
    private String extractFileId(MvcResult result) throws Exception {
        String responseBody = result.getResponse().getContentAsString();
        ApiResponse<UploadResult> response = objectMapper.readValue(
            responseBody,
            objectMapper.getTypeFactory().constructParametricType(
                ApiResponse.class,
                UploadResult.class
            )
        );
        return response.getData().getFileId();
    }
}

