package com.architectcgz.file.application.service.uploadpart.command;

import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UploadPartSyncCommandServiceTest {

    @Mock
    private UploadPartRepository uploadPartRepository;

    private UploadPartSyncCommandService uploadPartSyncCommandService;

    @BeforeEach
    void setUp() {
        uploadPartSyncCommandService = new UploadPartSyncCommandService(uploadPartRepository);
    }

    @Test
    void syncAllPartsShouldDelegateToRepository() {
        List<UploadPart> uploadParts = List.of(UploadPart.builder().taskId("task-001").partNumber(1).build());

        uploadPartSyncCommandService.syncAllParts("task-001", uploadParts);

        verify(uploadPartRepository).syncAllPartsToDatabase("task-001", uploadParts);
    }
}
