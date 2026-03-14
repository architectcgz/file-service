package com.architectcgz.file.application.service;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessLevelChangeTransactionHelperTest {

    @Mock
    private FileRecordRepository fileRecordRepository;

    @Mock
    private StorageObjectRepository storageObjectRepository;

    private AccessLevelChangeTransactionHelper helper;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        helper = new AccessLevelChangeTransactionHelper(fileRecordRepository, storageObjectRepository);
    }

    @Test
    void testUpdateAccessLevelOnlyFailureUsesStableErrorCode() {
        when(fileRecordRepository.updateAccessLevel("file-001", AccessLevel.PRIVATE)).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> helper.updateAccessLevelOnly("file-001", AccessLevel.PRIVATE)
        );

        assertEquals(FileServiceErrorCodes.UPDATE_ACCESS_LEVEL_FAILED, exception.getErrorCode());
        assertEquals(
                String.format(FileServiceErrorMessages.UPDATE_ACCESS_LEVEL_FAILED, "file-001"),
                exception.getMessage()
        );
    }

    @Test
    void testRebindToCopiedStorageFailureUsesStableErrorCode() {
        StorageObject copiedStorageObject = StorageObject.builder()
                .id("storage-002")
                .storagePath("private/copied-file")
                .bucketName("private-bucket")
                .build();

        when(storageObjectRepository.save(copiedStorageObject)).thenReturn(copiedStorageObject);
        when(fileRecordRepository.updateStorageBindingAndAccessLevel(
                "file-001",
                "storage-002",
                "private/copied-file",
                AccessLevel.PRIVATE
        )).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> helper.rebindToCopiedStorage("file-001", "storage-001", copiedStorageObject, AccessLevel.PRIVATE)
        );

        assertEquals(FileServiceErrorCodes.UPDATE_STORAGE_BINDING_FAILED, exception.getErrorCode());
        assertEquals(
                String.format(FileServiceErrorMessages.UPDATE_STORAGE_BINDING_FAILED, "file-001"),
                exception.getMessage()
        );
    }

    @Test
    void testRebindToCopiedStorageDecrementFailureUsesStableErrorCode() {
        StorageObject copiedStorageObject = StorageObject.builder()
                .id("storage-002")
                .storagePath("private/copied-file")
                .bucketName("private-bucket")
                .build();

        when(storageObjectRepository.save(copiedStorageObject)).thenReturn(copiedStorageObject);
        when(fileRecordRepository.updateStorageBindingAndAccessLevel(
                "file-001",
                "storage-002",
                "private/copied-file",
                AccessLevel.PRIVATE
        )).thenReturn(true);
        when(storageObjectRepository.decrementReferenceCount("storage-001")).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> helper.rebindToCopiedStorage("file-001", "storage-001", copiedStorageObject, AccessLevel.PRIVATE)
        );

        assertEquals(FileServiceErrorCodes.STORAGE_REFERENCE_DECREMENT_FAILED, exception.getErrorCode());
        assertEquals(
                String.format(FileServiceErrorMessages.STORAGE_REFERENCE_DECREMENT_FAILED, "storage-001"),
                exception.getMessage()
        );
    }
}
