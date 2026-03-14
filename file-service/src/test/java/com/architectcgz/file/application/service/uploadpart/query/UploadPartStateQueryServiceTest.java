package com.architectcgz.file.application.service.uploadpart.query;

import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadPartStateQueryServiceTest {

    @Mock
    private UploadPartRepository uploadPartRepository;
    @Mock
    private UploadPartMapper uploadPartMapper;

    private UploadPartStateQueryService uploadPartStateQueryService;

    @BeforeEach
    void setUp() {
        UploadPartRecordQueryService uploadPartRecordQueryService =
                new UploadPartRecordQueryService(uploadPartRepository, uploadPartMapper);
        uploadPartStateQueryService = new UploadPartStateQueryService(uploadPartRecordQueryService);
    }

    @Test
    void findUploadedPartShouldReturnRepositoryResult() {
        UploadPart uploadPart = UploadPart.builder()
                .taskId("task-001")
                .partNumber(2)
                .etag("etag-2")
                .build();
        when(uploadPartRepository.findByTaskIdAndPartNumber("task-001", 2)).thenReturn(Optional.of(uploadPart));

        Optional<UploadPart> result = uploadPartStateQueryService.findUploadedPart("task-001", 2);

        assertThat(result).contains(uploadPart);
    }
}
