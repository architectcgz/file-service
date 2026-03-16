package com.architectcgz.file.application.service;

import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.TenantRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadTransactionHelperTest {

    @Mock
    private StorageObjectRepository storageObjectRepository;

    @Mock
    private FileRecordRepository fileRecordRepository;

    @Mock
    private TenantUsageRepository tenantUsageRepository;
    @Mock
    private TenantRepository tenantRepository;

    private UploadTransactionHelper helper;

    @BeforeEach
    void setUp() {
        helper = new UploadTransactionHelper(
                storageObjectRepository,
                fileRecordRepository,
                tenantRepository,
                tenantUsageRepository
        );
    }

    @Test
    void saveNewUploadShouldPersistMetadataAndUsage() {
        StorageObject storageObject = StorageObject.builder().id("storage-001").storagePath("blog/a.png").build();
        FileRecord fileRecord = FileRecord.builder().id("file-001").appId("blog").storageObjectId("storage-001").build();
        when(tenantUsageRepository.incrementUsageIfWithinQuota("blog", 1024L)).thenReturn(true);

        helper.saveNewUpload(storageObject, fileRecord, 1024L);

        InOrder inOrder = inOrder(storageObjectRepository, fileRecordRepository, tenantUsageRepository);
        inOrder.verify(storageObjectRepository).save(storageObject);
        inOrder.verify(fileRecordRepository).save(fileRecord);
        inOrder.verify(tenantUsageRepository).incrementUsageIfWithinQuota("blog", 1024L);
    }
}
