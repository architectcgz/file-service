package com.architectcgz.file.application.service;

import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UploadTransactionHelperTest {

    @Mock
    private StorageObjectRepository storageObjectRepository;

    @Mock
    private FileRecordRepository fileRecordRepository;

    @Mock
    private TenantUsageRepository tenantUsageRepository;

    @Mock
    private UploadTaskRepository uploadTaskRepository;

    private UploadTransactionHelper helper;

    @BeforeEach
    void setUp() {
        helper = new UploadTransactionHelper(
                storageObjectRepository,
                fileRecordRepository,
                tenantUsageRepository,
                uploadTaskRepository
        );
    }

    @Test
    void saveNewUploadShouldPersistMetadataAndUsage() {
        StorageObject storageObject = StorageObject.builder().id("storage-001").storagePath("blog/a.png").build();
        FileRecord fileRecord = FileRecord.builder().id("file-001").appId("blog").storageObjectId("storage-001").build();

        helper.saveNewUpload(storageObject, fileRecord, 1024L);

        InOrder inOrder = inOrder(storageObjectRepository, fileRecordRepository, tenantUsageRepository);
        inOrder.verify(storageObjectRepository).save(storageObject);
        inOrder.verify(fileRecordRepository).save(fileRecord);
        inOrder.verify(tenantUsageRepository).incrementUsage("blog", 1024L);
    }

    @Test
    void saveCompletedInstantUploadShouldUpdateReferenceUsageAndTaskStatus() {
        UploadTask task = UploadTask.builder().id("task-001").appId("blog").status(UploadTaskStatus.UPLOADING).build();
        FileRecord fileRecord = FileRecord.builder().id("file-001").appId("blog").fileSize(2048L).build();

        helper.saveCompletedInstantUpload(task, "storage-001", fileRecord);

        InOrder inOrder = inOrder(storageObjectRepository, fileRecordRepository, tenantUsageRepository, uploadTaskRepository);
        inOrder.verify(storageObjectRepository).incrementReferenceCount("storage-001");
        inOrder.verify(fileRecordRepository).save(fileRecord);
        inOrder.verify(tenantUsageRepository).incrementUsage("blog", 2048L);
        inOrder.verify(uploadTaskRepository).updateStatus("task-001", UploadTaskStatus.COMPLETED);
    }

    @Test
    void saveCompletedUploadShouldPersistMetadataAndMarkTaskCompleted() {
        UploadTask task = UploadTask.builder().id("task-001").appId("blog").status(UploadTaskStatus.UPLOADING).build();
        StorageObject storageObject = StorageObject.builder().id("storage-001").storagePath("blog/a.png").build();
        FileRecord fileRecord = FileRecord.builder().id("file-001").appId("blog").storageObjectId("storage-001").fileSize(4096L).build();

        helper.saveCompletedUpload(task, storageObject, fileRecord);

        verify(storageObjectRepository).save(storageObject);
        verify(fileRecordRepository).save(fileRecord);
        verify(tenantUsageRepository).incrementUsage("blog", 4096L);
        verify(uploadTaskRepository).updateStatus("task-001", UploadTaskStatus.COMPLETED);
    }
}
