package com.architectcgz.file.application.service;

import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UploadPartTransactionHelperTest {

    @Mock
    private UploadPartRepository uploadPartRepository;

    private UploadPartTransactionHelper helper;

    @BeforeEach
    void setUp() {
        helper = new UploadPartTransactionHelper(uploadPartRepository);
    }

    @Test
    void savePartShouldPersistGeneratedUploadPart() {
        helper.savePart("task-001", 3, "etag-123", 2048L);

        ArgumentCaptor<UploadPart> captor = ArgumentCaptor.forClass(UploadPart.class);
        verify(uploadPartRepository).savePart(captor.capture());
        UploadPart savedPart = captor.getValue();
        assertThat(savedPart.getId()).isNotBlank();
        assertThat(savedPart.getTaskId()).isEqualTo("task-001");
        assertThat(savedPart.getPartNumber()).isEqualTo(3);
        assertThat(savedPart.getEtag()).isEqualTo("etag-123");
        assertThat(savedPart.getSize()).isEqualTo(2048L);
        assertThat(savedPart.getUploadedAt()).isNotNull();
    }
}
