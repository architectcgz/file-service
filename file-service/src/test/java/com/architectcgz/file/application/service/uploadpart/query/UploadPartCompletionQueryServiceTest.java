package com.architectcgz.file.application.service.uploadpart.query;

import com.architectcgz.file.application.service.uploadpart.assembler.UploadCompletedPartAssembler;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import com.architectcgz.file.infrastructure.repository.po.UploadPartPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadPartCompletionQueryServiceTest {

    @Mock
    private UploadPartRepository uploadPartRepository;
    @Mock
    private UploadPartMapper uploadPartMapper;

    private UploadPartCompletionQueryService uploadPartCompletionQueryService;

    @BeforeEach
    void setUp() {
        UploadPartRecordQueryService uploadPartRecordQueryService =
                new UploadPartRecordQueryService(uploadPartRepository, uploadPartMapper);
        uploadPartCompletionQueryService = new UploadPartCompletionQueryService(
                uploadPartRecordQueryService,
                new UploadCompletedPartAssembler()
        );
    }

    @Test
    void loadPersistedCompletedPartsShouldSortByPartNumber() {
        when(uploadPartMapper.selectByTaskId("task-001")).thenReturn(List.of(
                part(2, "etag-2"),
                part(1, "etag-1")
        ));

        List<CompletedPart> completedParts = uploadPartCompletionQueryService.loadPersistedCompletedParts("task-001");

        assertThat(completedParts).extracting(CompletedPart::partNumber).containsExactly(1, 2);
        assertThat(completedParts).extracting(CompletedPart::eTag).containsExactly("etag-1", "etag-2");
    }

    private UploadPartPO part(int partNumber, String etag) {
        UploadPartPO uploadPartPO = new UploadPartPO();
        uploadPartPO.setPartNumber(partNumber);
        uploadPartPO.setEtag(etag);
        return uploadPartPO;
    }
}
