package com.architectcgz.file.application.service;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDeleteTransactionHelperTest {

    @Mock
    private FileRecordRepository fileRecordRepository;

    @Mock
    private StorageObjectRepository storageObjectRepository;

    @Mock
    private TenantUsageRepository tenantUsageRepository;

    private FileDeleteTransactionHelper helper;

    @BeforeEach
    void setUp() {
        helper = new FileDeleteTransactionHelper(
                fileRecordRepository,
                storageObjectRepository,
                tenantUsageRepository
        );
    }

    @Test
    void testFindStorageObjectIfLastReferenceReturnsPresentWhenLastReference() {
        StorageObject storageObject = StorageObject.builder()
                .id("storage-001")
                .referenceCount(1)
                .build();
        when(storageObjectRepository.findById("storage-001")).thenReturn(Optional.of(storageObject));

        Optional<StorageObject> result = helper.findStorageObjectIfLastReference("storage-001");

        assertTrue(result.isPresent());
    }

    @Test
    void testFindStorageObjectIfLastReferenceReturnsEmptyWhenNotLastReference() {
        StorageObject storageObject = StorageObject.builder()
                .id("storage-001")
                .referenceCount(2)
                .build();
        when(storageObjectRepository.findById("storage-001")).thenReturn(Optional.of(storageObject));

        Optional<StorageObject> result = helper.findStorageObjectIfLastReference("storage-001");

        assertFalse(result.isPresent());
    }

    @Test
    void testCommitAdminDeleteFailureUsesStableErrorCode() {
        FileRecord fileRecord = FileRecord.builder()
                .id("file-001")
                .appId("blog")
                .fileSize(1024L)
                .storageObjectId("storage-001")
                .build();
        when(fileRecordRepository.deleteById("file-001")).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> helper.commitAdminDelete("file-001", fileRecord)
        );

        assertEquals(FileServiceErrorCodes.FILE_DELETE_FAILED, exception.getErrorCode());
        assertEquals(
                String.format(FileServiceErrorMessages.FILE_DELETE_FAILED, "file-001"),
                exception.getMessage()
        );
    }
}
