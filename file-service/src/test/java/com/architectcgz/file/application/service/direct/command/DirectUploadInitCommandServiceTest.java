package com.architectcgz.file.application.service.direct.command;

import com.architectcgz.file.application.dto.DirectUploadInitRequest;
import com.architectcgz.file.application.dto.DirectUploadInitResponse;
import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.UploadTransactionHelper;
import com.architectcgz.file.application.service.direct.assembler.DirectUploadPartResponseAssembler;
import com.architectcgz.file.application.service.direct.factory.DirectUploadObjectFactory;
import com.architectcgz.file.application.service.direct.storage.DirectUploadStorageService;
import com.architectcgz.file.application.service.uploadtask.factory.UploadTaskFactory;
import com.architectcgz.file.application.service.uploadtask.command.UploadTaskCommandService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.domain.service.TenantDomainService;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DirectUploadInitCommandService 单元测试")
class DirectUploadInitCommandServiceTest {

    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private UploadTaskRepository uploadTaskRepository;
    @Mock
    private StorageObjectRepository storageObjectRepository;
    @Mock
    private MultipartProperties multipartProperties;
    @Mock
    private FileTypeValidator fileTypeValidator;
    @Mock
    private TenantDomainService tenantDomainService;
    @Mock
    private UploadTransactionHelper uploadTransactionHelper;

    private DirectUploadInitCommandService directUploadInitCommandService;

    @BeforeEach
    void setUp() {
        UploadTaskCommandService uploadTaskCommandService =
                new UploadTaskCommandService(uploadTaskRepository, new UploadTaskFactory());
        UploadTaskQueryService uploadTaskQueryService = new UploadTaskQueryService(uploadTaskRepository);
        DirectUploadStorageService directUploadStorageService = new DirectUploadStorageService(s3StorageService);
        directUploadInitCommandService = new DirectUploadInitCommandService(
                directUploadStorageService,
                new DirectUploadObjectFactory(),
                new DirectUploadPartResponseAssembler(),
                uploadTaskQueryService,
                uploadTaskCommandService,
                storageObjectRepository,
                multipartProperties,
                fileTypeValidator,
                tenantDomainService,
                uploadTransactionHelper
        );
    }

    @Test
    @DisplayName("秒传命中时应按 public bucket 去重并返回公开地址")
    void initDirectUpload_shouldUsePublicBucketForInstantUpload() {
        DirectUploadInitRequest request = new DirectUploadInitRequest();
        request.setFileName("report.pdf");
        request.setFileSize(1024L);
        request.setContentType("application/pdf");
        request.setFileHash("hash-123");

        StorageObject storageObject = StorageObject.builder()
                .id("storage-001")
                .appId("blog")
                .fileHash("hash-123")
                .storagePath("blog/files/report.pdf")
                .bucketName("public-bucket")
                .fileSize(1024L)
                .contentType("application/pdf")
                .referenceCount(2)
                .build();

        when(s3StorageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-123", "public-bucket"))
                .thenReturn(Optional.of(storageObject));
        when(s3StorageService.getPublicUrl("public-bucket", "blog/files/report.pdf"))
                .thenReturn("https://cdn.example.com/blog/files/report.pdf");
        doNothing().when(tenantDomainService).checkQuota("blog", 1024L);
        doNothing().when(fileTypeValidator).validateFile("report.pdf", "application/pdf", 1024L);

        ArgumentCaptor<FileRecord> fileRecordCaptor = ArgumentCaptor.forClass(FileRecord.class);
        doNothing().when(uploadTransactionHelper)
                .saveInstantUpload(anyString(), fileRecordCaptor.capture(), anyLong());

        DirectUploadInitResponse response =
                directUploadInitCommandService.initDirectUpload("blog", request, "user-123");

        assertThat(response.getIsInstantUpload()).isTrue();
        assertThat(response.getFileUrl()).isEqualTo("https://cdn.example.com/blog/files/report.pdf");
        assertThat(fileRecordCaptor.getValue().getStorageObjectId()).isEqualTo("storage-001");
        verify(storageObjectRepository).findByFileHashAndBucket("blog", "hash-123", "public-bucket");
        verify(uploadTransactionHelper).saveInstantUpload("storage-001", fileRecordCaptor.getValue(), 1024L);
    }
}
