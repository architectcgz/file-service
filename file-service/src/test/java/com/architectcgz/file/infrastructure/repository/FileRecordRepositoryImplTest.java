package com.architectcgz.file.infrastructure.repository;

import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.infrastructure.repository.mapper.FileRecordMapper;
import com.architectcgz.file.infrastructure.repository.po.FileRecordPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileRecordRepositoryImpl 单元测试")
class FileRecordRepositoryImplTest {

    @Mock
    private FileRecordMapper fileRecordMapper;

    @InjectMocks
    private FileRecordRepositoryImpl fileRecordRepository;

    @Test
    @DisplayName("save 应保留 accessLevel 和 hashAlgorithm")
    void save_shouldPreserveAccessLevelAndHashAlgorithm() {
        FileRecord fileRecord = FileRecord.builder()
                .id("record-001")
                .appId("blog")
                .userId("user-123")
                .storageObjectId("storage-001")
                .originalFilename("private.pdf")
                .storagePath("blog/files/private.pdf")
                .fileSize(2048L)
                .contentType("application/pdf")
                .fileHash("hash-001")
                .hashAlgorithm("SHA256")
                .accessLevel(AccessLevel.PRIVATE)
                .status(FileStatus.COMPLETED)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        fileRecordRepository.save(fileRecord);

        ArgumentCaptor<FileRecordPO> captor = ArgumentCaptor.forClass(FileRecordPO.class);
        verify(fileRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getAccessLevel()).isEqualTo("private");
        assertThat(captor.getValue().getHashAlgorithm()).isEqualTo("SHA256");
    }

    @Test
    @DisplayName("findById 应正确映射 accessLevel 和 hashAlgorithm")
    void findById_shouldMapAccessLevelAndHashAlgorithm() {
        FileRecordPO po = new FileRecordPO();
        po.setId("record-001");
        po.setAppId("blog");
        po.setUserId("user-123");
        po.setStorageObjectId("storage-001");
        po.setOriginalFilename("private.pdf");
        po.setStoragePath("blog/files/private.pdf");
        po.setFileSize(2048L);
        po.setContentType("application/pdf");
        po.setFileHash("hash-001");
        po.setHashAlgorithm("SHA256");
        po.setAccessLevel("private");
        po.setStatus("COMPLETED");
        po.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        po.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        when(fileRecordMapper.selectById("record-001")).thenReturn(po);

        Optional<FileRecord> result = fileRecordRepository.findById("record-001");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getAccessLevel()).isEqualTo(AccessLevel.PRIVATE);
        assertThat(result.orElseThrow().getHashAlgorithm()).isEqualTo("SHA256");
    }
}
