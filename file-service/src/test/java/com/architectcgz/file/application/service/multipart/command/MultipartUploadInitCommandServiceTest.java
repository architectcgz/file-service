package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.dto.InitUploadRequest;
import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.multipart.factory.MultipartUploadObjectFactory;
import com.architectcgz.file.application.service.multipart.storage.MultipartUploadStorageService;
import com.architectcgz.file.application.service.uploadpart.query.UploadPartStateQueryService;
import com.architectcgz.file.application.service.uploadtask.factory.UploadTaskFactory;
import com.architectcgz.file.application.service.uploadtask.command.UploadTaskCommandService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.domain.service.TenantDomainService;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultipartUploadInitCommandService 单元测试")
class MultipartUploadInitCommandServiceTest {

    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private UploadTaskRepository uploadTaskRepository;
    @Mock
    private MultipartProperties multipartProperties;
    @Mock
    private FileTypeValidator fileTypeValidator;
    @Mock
    private TenantDomainService tenantDomainService;
    @Mock
    private UploadPartStateQueryService uploadPartStateQueryService;

    private MultipartUploadInitCommandService multipartUploadInitCommandService;

    @BeforeEach
    void setUp() {
        UploadTaskCommandService uploadTaskCommandService =
                new UploadTaskCommandService(uploadTaskRepository, new UploadTaskFactory());
        UploadTaskQueryService uploadTaskQueryService = new UploadTaskQueryService(uploadTaskRepository);
        multipartUploadInitCommandService = new MultipartUploadInitCommandService(
                new MultipartUploadStorageService(s3StorageService),
                new MultipartUploadObjectFactory(),
                uploadTaskQueryService,
                uploadTaskCommandService,
                uploadPartStateQueryService,
                multipartProperties,
                fileTypeValidator,
                tenantDomainService
        );
    }

    @Test
    @DisplayName("初始化分片上传时应在 public bucket 创建任务")
    void initUpload_shouldCreateMultipartUploadInPublicBucket() {
        InitUploadRequest request = new InitUploadRequest();
        request.setFileName("archive.zip");
        request.setFileSize(2048L);
        request.setContentType("application/zip");
        request.setFileHash("hash-456");

        when(s3StorageService.getBucketName(AccessLevel.PUBLIC)).thenReturn("public-bucket");
        when(s3StorageService.createMultipartUpload(any(), any(), eq("public-bucket"))).thenReturn("upload-001");
        when(multipartProperties.getChunkSize()).thenReturn(1024);
        when(multipartProperties.getMaxParts()).thenReturn(10);
        when(multipartProperties.getTaskExpireHours()).thenReturn(24);
        when(uploadTaskRepository.findByUserIdAndFileHash("blog", "user-123", "hash-456"))
                .thenReturn(Optional.empty());
        doNothing().when(tenantDomainService).checkQuota("blog", 2048L);
        doNothing().when(fileTypeValidator).validateFile("archive.zip", "application/zip", 2048L);

        multipartUploadInitCommandService.initUpload("blog", request, "user-123");

        verify(tenantDomainService).checkQuota("blog", 2048L);
        verify(s3StorageService).createMultipartUpload(any(), eq("application/zip"), eq("public-bucket"));
    }
}
