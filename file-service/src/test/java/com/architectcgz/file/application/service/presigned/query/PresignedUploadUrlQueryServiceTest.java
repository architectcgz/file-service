package com.architectcgz.file.application.service.presigned.query;

import com.architectcgz.file.application.dto.PresignedUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadResponse;
import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.presigned.factory.PresignedUploadObjectFactory;
import com.architectcgz.file.application.service.presigned.storage.PresignedUploadStorageService;
import com.architectcgz.file.application.service.presigned.validator.PresignedUploadAccessResolver;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PresignedUploadUrlQueryService 单元测试")
class PresignedUploadUrlQueryServiceTest {

    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private StorageObjectRepository storageObjectRepository;
    @Mock
    private FileRecordRepository fileRecordRepository;
    @Mock
    private FileTypeValidator fileTypeValidator;

    private PresignedUploadUrlQueryService presignedUploadUrlQueryService;

    @BeforeEach
    void setUp() {
        PresignedUploadStorageService presignedUploadStorageService = new PresignedUploadStorageService(
                s3StorageService,
                s3StorageService
        );
        ReflectionTestUtils.setField(presignedUploadStorageService, "presignedUrlExpireSeconds", 300);
        presignedUploadUrlQueryService = new PresignedUploadUrlQueryService(
                new PresignedUploadAccessResolver(),
                new PresignedStorageObjectQueryService(storageObjectRepository),
                new PresignedUploadObjectFactory(),
                presignedUploadStorageService,
                fileRecordRepository,
                fileTypeValidator
        );
    }

    @Test
    @DisplayName("生成预签名上传地址时应按 accessLevel 选择 bucket")
    void getPresignedUploadUrl_shouldUseAccessLevelBucket() {
        PresignedUploadRequest request = new PresignedUploadRequest();
        request.setFileName("avatar.png");
        request.setFileSize(512L);
        request.setContentType("image/png");
        request.setFileHash("hash-public");
        request.setAccessLevel("public");

        doReturn("public-bucket").when(s3StorageService).getBucketName(AccessLevel.PUBLIC);
        when(storageObjectRepository.findByFileHashAndBucket("blog", "hash-public", "public-bucket"))
                .thenReturn(Optional.empty());
        when(s3StorageService.generatePresignedPutUrl(any(), eq("image/png"), eq(300), eq("public-bucket")))
                .thenReturn("https://minio.example.com/presigned");

        PresignedUploadResponse response =
                presignedUploadUrlQueryService.getPresignedUploadUrl("blog", request, "user-1");

        assertThat(response.getPresignedUrl()).isEqualTo("https://minio.example.com/presigned");
        verify(storageObjectRepository).findByFileHashAndBucket("blog", "hash-public", "public-bucket");
    }

    @Test
    @DisplayName("accessLevel 非法时应返回稳定错误码")
    void getPresignedUploadUrl_shouldThrowStableErrorCodeWhenAccessLevelInvalid() {
        PresignedUploadRequest request = new PresignedUploadRequest();
        request.setFileName("avatar.png");
        request.setFileSize(512L);
        request.setContentType("image/png");
        request.setFileHash("hash-public");
        request.setAccessLevel("unsupported");

        assertThatThrownBy(() -> presignedUploadUrlQueryService.getPresignedUploadUrl("blog", request, "user-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不支持的访问级别: unsupported")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(FileServiceErrorCodes.UNSUPPORTED_ACCESS_LEVEL));
    }
}
