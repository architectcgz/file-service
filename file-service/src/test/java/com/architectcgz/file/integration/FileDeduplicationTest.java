package com.architectcgz.file.integration;

import com.architectcgz.file.common.result.ApiResponse;
import com.architectcgz.file.config.TestStorageConfig;
import com.architectcgz.file.interfaces.dto.UploadResult;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
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
 * File Deduplication Integration Test
 * 
 * Tests:
 * - Same file uploaded multiple times within same appId shares storage
 * - Different files create separate storage objects
 * - Cross-appId deduplication is isolated
 * - Reference counting works correctly
 * - Deletion only removes physical file when reference count reaches zero
 * 
 * Validates: Requirements 2.4, 2.5, 3.1, 3.2, 3.3
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
@DisplayName("File Deduplication Integration Test")
class FileDeduplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileRecordRepository fileRecordRepository;

    @Autowired
    private StorageObjectRepository storageObjectRepository;

    private MockMultipartFile testFile1;
    private MockMultipartFile testFile2;
    private static final String BLOG_APP_ID = "blog";
    private static final String IM_APP_ID = "im";
    private static final String USER_ID_1 = "12345";
    private static final String USER_ID_2 = "67890";

    @BeforeEach
    void setUp() {
        // Create test files with same content
        byte[] content1 = "identical-file-content-for-deduplication-test".getBytes(StandardCharsets.UTF_8);
        testFile1 = new MockMultipartFile(
            "file",
            "file1.txt",
            "text/plain",
            content1
        );

        // Same content, different filename
        testFile2 = new MockMultipartFile(
            "file",
            "file2.txt",
            "text/plain",
            content1
        );
    }

    @Test
    @DisplayName("Same file uploaded twice in same appId should share storage object")
    void sameFile_sameAppId_shouldShareStorage() throws Exception {
        // 1. First upload
        MvcResult upload1 = mockMvc.perform(multipart("/api/v1/upload/file")
                .file(testFile1)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andReturn();

        String fileId1 = extractFileId(upload1);

        // 2. Second upload (same content, different filename)
        MvcResult upload2 = mockMvc.perform(multipart("/api/v1/upload/file")
                .file(testFile2)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andReturn();

        String fileId2 = extractFileId(upload2);

        // 3. Verify two different file records were created
        assertThat(fileId1).isNotEqualTo(fileId2);

        FileRecord record1 = fileRecordRepository.findById(fileId1).orElse(null);
        FileRecord record2 = fileRecordRepository.findById(fileId2).orElse(null);

        assertThat(record1).isNotNull();
        assertThat(record2).isNotNull();
        assertThat(record1.getOriginalFilename()).isEqualTo("file1.txt");
        assertThat(record2.getOriginalFilename()).isEqualTo("file2.txt");

        // 4. Verify both records share the same storage object (deduplication)
        assertThat(record1.getStorageObjectId()).isNotNull();
        assertThat(record1.getStorageObjectId()).isEqualTo(record2.getStorageObjectId());

        // 5. Verify storage object has reference count of 2
        StorageObject storageObject = storageObjectRepository.findById(
            record1.getStorageObjectId()
        ).orElse(null);

        assertThat(storageObject).isNotNull();
        assertThat(storageObject.getReferenceCount()).isEqualTo(2);
        assertThat(storageObject.getAppId()).isEqualTo(BLOG_APP_ID);
    }

    @Test
    @DisplayName("Same file in different appIds should NOT share storage")
    void sameFile_differentAppIds_shouldNotShareStorage() throws Exception {
        // 1. Blog app uploads file
        MvcResult blogUpload = mockMvc.perform(multipart("/api/v1/upload/file")
                .file(testFile1)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_1)))
            .andExpect(status().isOk())
            .andReturn();

        String blogFileId = extractFileId(blogUpload);

        // 2. IM app uploads same file
        MockMultipartFile sameFile = new MockMultipartFile(
            "file",
            "file-im.txt",
            "text/plain",
            testFile1.getBytes()
        );

        MvcResult imUpload = mockMvc.perform(multipart("/api/v1/upload/file")
                .file(sameFile)
                .header("X-App-Id", IM_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_2)))
            .andExpect(status().isOk())
            .andReturn();

        String imFileId = extractFileId(imUpload);

        // 3. Verify different storage objects (appId isolation)
        FileRecord blogRecord = fileRecordRepository.findById(blogFileId).orElse(null);
        FileRecord imRecord = fileRecordRepository.findById(imFileId).orElse(null);

        assertThat(blogRecord).isNotNull();
        assertThat(imRecord).isNotNull();
        assertThat(blogRecord.getStorageObjectId())
            .isNotEqualTo(imRecord.getStorageObjectId());

        // 4. Verify each storage object has reference count of 1
        StorageObject blogStorage = storageObjectRepository.findById(
            blogRecord.getStorageObjectId()
        ).orElse(null);
        StorageObject imStorage = storageObjectRepository.findById(
            imRecord.getStorageObjectId()
        ).orElse(null);

        assertThat(blogStorage).isNotNull();
        assertThat(imStorage).isNotNull();
        assertThat(blogStorage.getReferenceCount()).isEqualTo(1);
        assertThat(imStorage.getReferenceCount()).isEqualTo(1);
        assertThat(blogStorage.getAppId()).isEqualTo(BLOG_APP_ID);
        assertThat(imStorage.getAppId()).isEqualTo(IM_APP_ID);
    }

    @Test
    @DisplayName("Different files should create separate storage objects")
    void differentFiles_shouldCreateSeparateStorage() throws Exception {
        // 1. Upload first file
        MvcResult upload1 = mockMvc.perform(multipart("/api/v1/upload/file")
                .file(testFile1)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_1)))
            .andExpect(status().isOk())
            .andReturn();

        String fileId1 = extractFileId(upload1);

        // 2. Upload different file
        MockMultipartFile differentFile = new MockMultipartFile(
            "file",
            "different.txt",
            "text/plain",
            "completely-different-content".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult upload2 = mockMvc.perform(multipart("/api/v1/upload/file")
                .file(differentFile)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_1)))
            .andExpect(status().isOk())
            .andReturn();

        String fileId2 = extractFileId(upload2);

        // 3. Verify different storage objects
        FileRecord record1 = fileRecordRepository.findById(fileId1).orElse(null);
        FileRecord record2 = fileRecordRepository.findById(fileId2).orElse(null);

        assertThat(record1).isNotNull();
        assertThat(record2).isNotNull();
        assertThat(record1.getStorageObjectId())
            .isNotEqualTo(record2.getStorageObjectId());

        // 4. Verify each has reference count of 1
        StorageObject storage1 = storageObjectRepository.findById(
            record1.getStorageObjectId()
        ).orElse(null);
        StorageObject storage2 = storageObjectRepository.findById(
            record2.getStorageObjectId()
        ).orElse(null);

        assertThat(storage1).isNotNull();
        assertThat(storage2).isNotNull();
        assertThat(storage1.getReferenceCount()).isEqualTo(1);
        assertThat(storage2.getReferenceCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Deleting one file should decrement reference count but keep storage")
    void deleteOneFile_shouldDecrementReferenceCount() throws Exception {
        // 1. Upload same file twice
        MvcResult upload1 = mockMvc.perform(multipart("/api/v1/upload/file")
                .file(testFile1)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_1)))
            .andExpect(status().isOk())
            .andReturn();

        String fileId1 = extractFileId(upload1);

        MvcResult upload2 = mockMvc.perform(multipart("/api/v1/upload/file")
                .file(testFile2)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_1)))
            .andExpect(status().isOk())
            .andReturn();

        String fileId2 = extractFileId(upload2);

        // 2. Verify reference count is 2
        FileRecord record1 = fileRecordRepository.findById(fileId1).orElse(null);
        String storageObjectId = record1.getStorageObjectId();
        
        StorageObject storageBefore = storageObjectRepository.findById(storageObjectId).orElse(null);
        assertThat(storageBefore).isNotNull();
        assertThat(storageBefore.getReferenceCount()).isEqualTo(2);

        // 3. Delete first file
        mockMvc.perform(delete("/api/v1/upload/" + fileId1)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        // 4. Verify reference count decremented to 1
        StorageObject storageAfter = storageObjectRepository.findById(storageObjectId).orElse(null);
        assertThat(storageAfter).isNotNull();
        assertThat(storageAfter.getReferenceCount()).isEqualTo(1);

        // 5. Verify second file still accessible
        mockMvc.perform(get("/api/v1/files/" + fileId2 + "/url")
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_1)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
        
        // 6. Verify first file (deleted) is not accessible - should return 404
        mockMvc.perform(get("/api/v1/files/" + fileId1 + "/url")
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_1)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("文件已被删除: " + fileId1));
    }

    @Test
    @DisplayName("Multiple users uploading same file should share storage within appId")
    void multipleUsers_sameFile_sameAppId_shouldShareStorage() throws Exception {
        // 1. User 1 uploads file
        MvcResult upload1 = mockMvc.perform(multipart("/api/v1/upload/file")
                .file(testFile1)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_1)))
            .andExpect(status().isOk())
            .andReturn();

        String fileId1 = extractFileId(upload1);

        // 2. User 2 uploads same file
        MockMultipartFile sameFile = new MockMultipartFile(
            "file",
            "user2-file.txt",
            "text/plain",
            testFile1.getBytes()
        );

        MvcResult upload2 = mockMvc.perform(multipart("/api/v1/upload/file")
                .file(sameFile)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_2)))
            .andExpect(status().isOk())
            .andReturn();

        String fileId2 = extractFileId(upload2);

        // 3. Verify different file records (different users)
        FileRecord record1 = fileRecordRepository.findById(fileId1).orElse(null);
        FileRecord record2 = fileRecordRepository.findById(fileId2).orElse(null);

        assertThat(record1).isNotNull();
        assertThat(record2).isNotNull();
        assertThat(record1.getUserId()).isEqualTo(USER_ID_1);
        assertThat(record2.getUserId()).isEqualTo(USER_ID_2);

        // 4. Verify shared storage object
        assertThat(record1.getStorageObjectId()).isEqualTo(record2.getStorageObjectId());

        StorageObject storage = storageObjectRepository.findById(
            record1.getStorageObjectId()
        ).orElse(null);

        assertThat(storage).isNotNull();
        assertThat(storage.getReferenceCount()).isEqualTo(2);

        // 5. User 1 deletes their file - User 2's file should still work
        mockMvc.perform(delete("/api/v1/upload/" + fileId1)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_1)))
            .andExpect(status().isOk());

        // 6. User 2 can still access their file
        mockMvc.perform(get("/api/v1/files/" + fileId2 + "/url")
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_2)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
        
        // 6.1. User 1's deleted file should return 404
        mockMvc.perform(get("/api/v1/files/" + fileId1 + "/url")
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_1)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404));

        // 7. Verify reference count is now 1
        StorageObject storageAfterDelete = storageObjectRepository.findById(
            record1.getStorageObjectId()
        ).orElse(null);
        assertThat(storageAfterDelete).isNotNull();
        assertThat(storageAfterDelete.getReferenceCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Storage object query should filter by appId and hash")
    void storageObjectQuery_shouldFilterByAppIdAndHash() throws Exception {
        // 1. Blog app uploads file
        MvcResult blogUpload = mockMvc.perform(multipart("/api/v1/upload/file")
                .file(testFile1)
                .header("X-App-Id", BLOG_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_1)))
            .andExpect(status().isOk())
            .andReturn();

        String blogFileId = extractFileId(blogUpload);

        // 2. IM app uploads same file
        MockMultipartFile sameFile = new MockMultipartFile(
            "file",
            "im-file.txt",
            "text/plain",
            testFile1.getBytes()
        );

        MvcResult imUpload = mockMvc.perform(multipart("/api/v1/upload/file")
                .file(sameFile)
                .header("X-App-Id", IM_APP_ID)
                .header("X-User-Id", String.valueOf(USER_ID_2)))
            .andExpect(status().isOk())
            .andReturn();

        String imFileId = extractFileId(imUpload);

        // 3. Verify two separate storage objects exist
        FileRecord blogRecord = fileRecordRepository.findById(blogFileId).orElse(null);
        FileRecord imRecord = fileRecordRepository.findById(imFileId).orElse(null);

        assertThat(blogRecord).isNotNull();
        assertThat(imRecord).isNotNull();

        StorageObject blogStorage = storageObjectRepository.findById(
            blogRecord.getStorageObjectId()
        ).orElse(null);
        StorageObject imStorage = storageObjectRepository.findById(
            imRecord.getStorageObjectId()
        ).orElse(null);

        assertThat(blogStorage).isNotNull();
        assertThat(imStorage).isNotNull();

        // 4. Verify same file hash but different appIds
        assertThat(blogStorage.getFileHash()).isEqualTo(imStorage.getFileHash());
        assertThat(blogStorage.getAppId()).isEqualTo(BLOG_APP_ID);
        assertThat(imStorage.getAppId()).isEqualTo(IM_APP_ID);

        // 5. Verify storage paths contain correct appId prefix
        assertThat(blogStorage.getStoragePath()).startsWith(BLOG_APP_ID + "/");
        assertThat(imStorage.getStoragePath()).startsWith(IM_APP_ID + "/");
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

