package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.BatchDeleteResult;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import com.architectcgz.file.infrastructure.cache.FileUrlCacheManager;
import com.architectcgz.file.infrastructure.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FileManagementServiceTest {

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

    @Test
    void testDeleteFileWithoutAdminIdentityThrowsAccessDenied() {
        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> fileManagementService.deleteFile("file-001", null)
        );

        assertEquals("未获取到管理员身份", exception.getMessage());
        verifyNoInteractions(fileRecordRepository, storageService, auditLogService, fileUrlCacheManager, deleteTransactionHelper);
    }

    @Test
    void testBatchDeleteFilesWithoutAdminIdentityThrowsAccessDenied() {
        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> fileManagementService.batchDeleteFiles(List.of("file-001"), " ")
        );

        assertEquals("未获取到管理员身份", exception.getMessage());
        verifyNoInteractions(fileRecordRepository, storageService, auditLogService, fileUrlCacheManager, deleteTransactionHelper);
    }
}
