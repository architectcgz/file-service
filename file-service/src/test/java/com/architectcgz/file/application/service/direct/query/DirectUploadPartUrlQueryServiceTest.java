package com.architectcgz.file.application.service.direct.query;

import com.architectcgz.file.application.dto.DirectUploadPartUrlRequest;
import com.architectcgz.file.application.service.direct.storage.DirectUploadStorageService;
import com.architectcgz.file.application.service.direct.validator.DirectUploadTaskValidator;
import com.architectcgz.file.application.service.uploadtask.factory.UploadTaskFactory;
import com.architectcgz.file.application.service.uploadtask.command.UploadTaskCommandService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.infrastructure.config.AccessProperties;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DirectUploadPartUrlQueryService 单元测试")
class DirectUploadPartUrlQueryServiceTest {

    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private UploadTaskRepository uploadTaskRepository;
    @Mock
    private AccessProperties accessProperties;

    private DirectUploadPartUrlQueryService directUploadPartUrlQueryService;

    @BeforeEach
    void setUp() {
        UploadTaskCommandService uploadTaskCommandService =
                new UploadTaskCommandService(uploadTaskRepository, new UploadTaskFactory());
        UploadTaskQueryService uploadTaskQueryService = new UploadTaskQueryService(uploadTaskRepository);
        DirectUploadTaskValidator directUploadTaskValidator = new DirectUploadTaskValidator(uploadTaskCommandService);
        directUploadPartUrlQueryService =
                new DirectUploadPartUrlQueryService(
                        directUploadTaskValidator,
                        new DirectUploadStorageService(s3StorageService),
                        uploadTaskQueryService,
                        accessProperties
                );
    }

    @Test
    @DisplayName("获取直传分片地址时 appId 不匹配应拒绝")
    void getPartUploadUrls_shouldRejectWhenAppIdMismatch() {
        UploadTask task = UploadTask.builder()
                .id("task-001")
                .appId("blog")
                .userId("user-123")
                .status(UploadTaskStatus.UPLOADING)
                .totalParts(2)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        DirectUploadPartUrlRequest request = new DirectUploadPartUrlRequest();
        request.setTaskId("task-001");
        request.setPartNumbers(List.of(1));

        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> directUploadPartUrlQueryService.getPartUploadUrls("im", request, "user-123"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("获取直传分片地址时重复分片号应返回稳定错误码")
    void getPartUploadUrls_shouldReturnStableErrorCodeWhenPartNumbersDuplicated() {
        UploadTask task = UploadTask.builder()
                .id("task-001")
                .appId("blog")
                .userId("user-123")
                .status(UploadTaskStatus.UPLOADING)
                .totalParts(2)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        DirectUploadPartUrlRequest request = new DirectUploadPartUrlRequest();
        request.setTaskId("task-001");
        request.setPartNumbers(List.of(1, 1));

        when(uploadTaskRepository.findById("task-001")).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> directUploadPartUrlQueryService.getPartUploadUrls("blog", request, "user-123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分片编号重复: 1")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(FileServiceErrorCodes.PART_NUMBER_DUPLICATED));
    }
}
