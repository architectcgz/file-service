package com.architectcgz.file.application.service;

import com.architectcgz.file.common.exception.FileNotFoundException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import com.architectcgz.file.infrastructure.cache.FileUrlCacheManager;
import com.architectcgz.file.infrastructure.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileManagementService 缓存清除功能单元测试
 *
 * 测试范围：
 * - 文件删除时通过 FileUrlCacheManager 清除缓存
 * - 缓存清除失败不影响文件删除
 * - 批量删除时清除所有文件缓存
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FileManagementService 缓存清除功能测试")
class FileManagementServiceCacheTest {

    @Mock
    private FileRecordRepository fileRecordRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private TenantUsageRepository tenantUsageRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private FileUrlCacheManager fileUrlCacheManager;
    @Mock
    private FileDeleteTransactionHelper deleteTransactionHelper;

    @InjectMocks
    private FileManagementService fileManagementService;

    private FileRecord testFileRecord;
    private StorageObject testStorageObject;

    @BeforeEach
    void setUp() {
        testFileRecord = FileRecord.builder()
                .id("file-001").appId("blog").userId("user-123")
                .storageObjectId("storage-001").originalFilename("test.jpg")
                .storagePath("2026/02/11/user-123/test.jpg").fileSize(1024L)
                .contentType("image/jpeg").fileHash("abc123").hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED).accessLevel(AccessLevel.PUBLIC)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        testStorageObject = StorageObject.builder()
                .id("storage-001")
                .appId("blog")
                .storagePath(testFileRecord.getStoragePath())
                .bucketName("public-bucket")
                .fileHash("abc123")
                .hashAlgorithm("MD5")
                .fileSize(testFileRecord.getFileSize())
                .contentType(testFileRecord.getContentType())
                .referenceCount(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ========== 缓存清除测试 ==========

    @Test
    @DisplayName("文件删除 - 通过 FileUrlCacheManager 清除缓存")
    void testDeleteFile_EvictsCacheViaManager() {
        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(testFileRecord));
        when(deleteTransactionHelper.findStorageObjectIfLastReference("storage-001")).thenReturn(Optional.of(testStorageObject));
        doNothing().when(storageService).delete("public-bucket", testFileRecord.getStoragePath());
        doNothing().when(deleteTransactionHelper).commitAdminDelete("file-001", testFileRecord);
        doNothing().when(auditLogService).log(any());

        fileManagementService.deleteFile("file-001", "admin-001");

        verify(fileUrlCacheManager).evict("file-001");
        verify(deleteTransactionHelper).commitAdminDelete("file-001", testFileRecord);
        verify(storageService).delete("public-bucket", testFileRecord.getStoragePath());
    }

    @Test
    @DisplayName("文件删除 - 缓存清除失败不影响文件删除")
    void testDeleteFile_CacheEvictFailureDoesNotAffectDeletion() {
        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(testFileRecord));
        when(deleteTransactionHelper.findStorageObjectIfLastReference("storage-001")).thenReturn(Optional.of(testStorageObject));
        doNothing().when(storageService).delete("public-bucket", testFileRecord.getStoragePath());
        doNothing().when(deleteTransactionHelper).commitAdminDelete("file-001", testFileRecord);
        doNothing().when(auditLogService).log(any());
        // FileUrlCacheManager.evict 内部已 catch 异常，这里模拟不抛出
        doNothing().when(fileUrlCacheManager).evict(anyString());

        assertDoesNotThrow(() -> fileManagementService.deleteFile("file-001", "admin-001"));

        verify(deleteTransactionHelper).commitAdminDelete("file-001", testFileRecord);
        verify(storageService).delete("public-bucket", testFileRecord.getStoragePath());
    }

    // ========== 边界情况测试 ==========

    @Test
    @DisplayName("文件不存在 - 抛出异常，不清除缓存")
    void testDeleteFile_FileNotFound() {
        when(fileRecordRepository.findById("non-existent")).thenReturn(Optional.empty());

        FileNotFoundException ex = assertThrows(FileNotFoundException.class,
                () -> fileManagementService.deleteFile("non-existent", "admin-001"));

        assertEquals("文件不存在: non-existent", ex.getMessage());
        verify(fileRecordRepository, never()).deleteById(anyString());
        verify(fileUrlCacheManager, never()).evict(anyString());
    }

    @Test
    @DisplayName("存储删除失败 - 抛出异常，不清除缓存")
    void testDeleteFile_StorageDeletionFails() {
        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(testFileRecord));
        when(deleteTransactionHelper.findStorageObjectIfLastReference("storage-001")).thenReturn(Optional.of(testStorageObject));
        doThrow(new RuntimeException("Storage deletion failed"))
                .when(storageService).delete("public-bucket", testFileRecord.getStoragePath());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> fileManagementService.deleteFile("file-001", "admin-001"));

        assertTrue(ex.getMessage().contains("Storage deletion failed"));
        verify(deleteTransactionHelper, never()).commitAdminDelete(anyString(), any());
        verify(fileUrlCacheManager, never()).evict(anyString());
    }

    // ========== 批量删除缓存清除测试 ==========

    @Test
    @DisplayName("批量删除 - 清除所有文件的缓存")
    void testBatchDeleteFiles_EvictsAllCaches() {
        FileRecord file2 = FileRecord.builder()
                .id("file-002").appId("blog").userId("user-123")
                .storageObjectId("storage-002").originalFilename("test2.jpg")
                .storagePath("2026/02/11/user-123/test2.jpg").fileSize(2048L)
                .contentType("image/jpeg").fileHash("def456").hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED).accessLevel(AccessLevel.PUBLIC)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(fileRecordRepository.findById("file-001")).thenReturn(Optional.of(testFileRecord));
        when(fileRecordRepository.findById("file-002")).thenReturn(Optional.of(file2));
        StorageObject file2StorageObject = StorageObject.builder()
                .id("storage-002")
                .appId("blog")
                .storagePath(file2.getStoragePath())
                .bucketName("public-bucket")
                .referenceCount(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(deleteTransactionHelper.findStorageObjectIfLastReference("storage-001")).thenReturn(Optional.of(testStorageObject));
        when(deleteTransactionHelper.findStorageObjectIfLastReference("storage-002")).thenReturn(Optional.of(file2StorageObject));
        doNothing().when(storageService).delete(eq("public-bucket"), anyString());
        doNothing().when(deleteTransactionHelper).commitAdminDelete(anyString(), any(FileRecord.class));
        doNothing().when(auditLogService).log(any());

        fileManagementService.batchDeleteFiles(Arrays.asList("file-001", "file-002"), "admin-001");

        verify(fileUrlCacheManager).evict("file-001");
        verify(fileUrlCacheManager).evict("file-002");
        verify(deleteTransactionHelper).commitAdminDelete("file-001", testFileRecord);
        verify(deleteTransactionHelper).commitAdminDelete("file-002", file2);
    }
}
