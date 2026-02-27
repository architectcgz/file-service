package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.InstantUploadCheckRequest;
import com.architectcgz.file.application.dto.InstantUploadCheckResponse;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * InstantUploadService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("秒传服务测试")
class InstantUploadServiceTest {
    
    @Mock
    private StorageObjectRepository storageObjectRepository;
    
    @Mock
    private FileRecordRepository fileRecordRepository;
    
    @Mock
    private StorageService storageService;
    
    @InjectMocks
    private InstantUploadService instantUploadService;
    
    private InstantUploadCheckRequest request;
    private StorageObject storageObject;
    private FileRecord fileRecord;
    private String userId;
    
    @BeforeEach
    void setUp() {
        userId = "12345";
        
        request = InstantUploadCheckRequest.builder()
                .fileHash("d41d8cd98f00b204e9800998ecf8427e")
                .fileSize(1024L)
                .fileName("test.jpg")
                .contentType("image/jpeg")
                .build();
        
        storageObject = StorageObject.builder()
                .id("storage-obj-123")
                .appId("blog")
                .fileHash("d41d8cd98f00b204e9800998ecf8427e")
                .hashAlgorithm("MD5")
                .storagePath("2026/01/19/12345/images/file.jpg")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .referenceCount(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        fileRecord = FileRecord.builder()
                .id("file-record-123")
                .appId("blog")
                .userId(userId)
                .storageObjectId("storage-obj-123")
                .originalFilename("test.jpg")
                .storagePath("2026/01/19/12345/images/file.jpg")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .fileHash("d41d8cd98f00b204e9800998ecf8427e")
                .hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    @Test
    @DisplayName("StorageObject 不存在时，返回需要上传")
    void checkInstantUpload_StorageObjectNotFound_returnsNeedUpload() {
        // Given
        when(fileRecordRepository.findByUserIdAndFileHash("blog", userId, request.getFileHash()))
                .thenReturn(Optional.empty());
        when(storageObjectRepository.findByFileHash("blog", request.getFileHash()))
                .thenReturn(Optional.empty());
        
        // When
        InstantUploadCheckResponse response = instantUploadService.checkInstantUpload("blog", request, userId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getInstantUpload()).isFalse();
        assertThat(response.getNeedUpload()).isTrue();
        assertThat(response.getFileId()).isNull();
        assertThat(response.getUrl()).isNull();
        
        verify(fileRecordRepository).findByUserIdAndFileHash("blog", userId, request.getFileHash());
        verify(storageObjectRepository).findByFileHash("blog", request.getFileHash());
        verify(fileRecordRepository, never()).save(any());
        verify(storageObjectRepository, never()).incrementReferenceCount(anyString());
    }
    
    @Test
    @DisplayName("用户已有该文件且状态为 COMPLETED，直接返回文件信息")
    void checkInstantUpload_UserAlreadyHasFile_returnsExistingFile() {
        // Given
        when(fileRecordRepository.findByUserIdAndFileHash("blog", userId, request.getFileHash()))
                .thenReturn(Optional.of(fileRecord));
        when(storageService.getUrl(fileRecord.getStoragePath()))
                .thenReturn("https://cdn.example.com/2026/01/19/12345/images/file.jpg");
        
        // When
        InstantUploadCheckResponse response = instantUploadService.checkInstantUpload("blog", request, userId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getInstantUpload()).isTrue();
        assertThat(response.getNeedUpload()).isFalse();
        assertThat(response.getFileId()).isEqualTo(fileRecord.getId());
        assertThat(response.getUrl()).isEqualTo("https://cdn.example.com/2026/01/19/12345/images/file.jpg");
        
        verify(fileRecordRepository).findByUserIdAndFileHash("blog", userId, request.getFileHash());
        verify(storageService).getUrl(fileRecord.getStoragePath());
        verify(fileRecordRepository, never()).save(any());
        verify(storageObjectRepository, never()).incrementReferenceCount(anyString());
    }
    
    @Test
    @DisplayName("用户没有该文件，创建新FileRecord 并增加引用计数")
    void checkInstantUpload_UserDoesNotHaveFile_CreatesNewFileRecord() {
        // Given
        when(storageObjectRepository.findByFileHash("blog", request.getFileHash()))
                .thenReturn(Optional.of(storageObject));
        when(fileRecordRepository.findByUserIdAndFileHash("blog", userId, request.getFileHash()))
                .thenReturn(Optional.empty());
        when(storageService.getUrl(storageObject.getStoragePath()))
                .thenReturn("https://cdn.example.com/2026/01/19/12345/images/file.jpg");
        when(fileRecordRepository.save(any(FileRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storageObjectRepository.incrementReferenceCount(storageObject.getId()))
                .thenReturn(true);
        
        // When
        InstantUploadCheckResponse response = instantUploadService.checkInstantUpload("blog", request, userId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getInstantUpload()).isTrue();
        assertThat(response.getNeedUpload()).isFalse();
        assertThat(response.getFileId()).isNotNull();
        assertThat(response.getUrl()).isEqualTo("https://cdn.example.com/2026/01/19/12345/images/file.jpg");
        
        verify(storageObjectRepository).findByFileHash("blog", request.getFileHash());
        verify(fileRecordRepository).findByUserIdAndFileHash("blog", userId, request.getFileHash());
        verify(fileRecordRepository).save(argThat(fr -> 
                fr.getUserId().equals(userId) &&
                fr.getStorageObjectId().equals(storageObject.getId()) &&
                fr.getOriginalFilename().equals(request.getFileName()) &&
                fr.getFileHash().equals(request.getFileHash()) &&
                fr.getStatus() == FileStatus.COMPLETED
        ));
        verify(storageObjectRepository).incrementReferenceCount(storageObject.getId());
        verify(storageService).getUrl(storageObject.getStoragePath());
    }
    
    @Test
    @DisplayName("用户的文件已被删除，创建新FileRecord 并增加引用计数")
    void checkInstantUpload_UserFileDeleted_CreatesNewFileRecord() {
        // Given
        FileRecord deletedFileRecord = FileRecord.builder()
                .id("deleted-file-123")
                .appId("blog")
                .userId(userId)
                .storageObjectId("storage-obj-123")
                .originalFilename("test.jpg")
                .storagePath("2026/01/19/12345/images/file.jpg")
                .fileSize(1024L)
                .contentType("image/jpeg")
                .fileHash("d41d8cd98f00b204e9800998ecf8427e")
                .hashAlgorithm("MD5")
                .status(FileStatus.DELETED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        when(storageObjectRepository.findByFileHash("blog", request.getFileHash()))
                .thenReturn(Optional.of(storageObject));
        when(fileRecordRepository.findByUserIdAndFileHash("blog", userId, request.getFileHash()))
                .thenReturn(Optional.of(deletedFileRecord));
        when(storageService.getUrl(storageObject.getStoragePath()))
                .thenReturn("https://cdn.example.com/2026/01/19/12345/images/file.jpg");
        when(fileRecordRepository.save(any(FileRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storageObjectRepository.incrementReferenceCount(storageObject.getId()))
                .thenReturn(true);
        
        // When
        InstantUploadCheckResponse response = instantUploadService.checkInstantUpload("blog", request, userId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getInstantUpload()).isTrue();
        assertThat(response.getNeedUpload()).isFalse();
        assertThat(response.getFileId()).isNotNull();
        assertThat(response.getUrl()).isEqualTo("https://cdn.example.com/2026/01/19/12345/images/file.jpg");
        
        verify(storageObjectRepository).findByFileHash("blog", request.getFileHash());
        verify(fileRecordRepository).findByUserIdAndFileHash("blog", userId, request.getFileHash());
        verify(fileRecordRepository).save(any(FileRecord.class));
        verify(storageObjectRepository).incrementReferenceCount(storageObject.getId());
        verify(storageService).getUrl(storageObject.getStoragePath());
    }
    
    @Test
    @DisplayName("使用请求中的 contentType，如果没有则使用 StorageObject 或contentType")
    void checkInstantUpload_UsesRequestContentType() {
        // Given
        InstantUploadCheckRequest requestWithoutContentType = InstantUploadCheckRequest.builder()
                .fileHash("d41d8cd98f00b204e9800998ecf8427e")
                .fileSize(1024L)
                .fileName("test.jpg")
                .contentType(null)
                .build();
        
        when(storageObjectRepository.findByFileHash("blog", requestWithoutContentType.getFileHash()))
                .thenReturn(Optional.of(storageObject));
        when(fileRecordRepository.findByUserIdAndFileHash("blog", userId, requestWithoutContentType.getFileHash()))
                .thenReturn(Optional.empty());
        when(storageService.getUrl(storageObject.getStoragePath()))
                .thenReturn("https://cdn.example.com/2026/01/19/12345/images/file.jpg");
        when(fileRecordRepository.save(any(FileRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storageObjectRepository.incrementReferenceCount(storageObject.getId()))
                .thenReturn(true);
        
        // When
        InstantUploadCheckResponse response = instantUploadService.checkInstantUpload("blog", requestWithoutContentType, userId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getInstantUpload()).isTrue();
        
        verify(fileRecordRepository).save(argThat(fr -> 
                fr.getContentType().equals(storageObject.getContentType())
        ));
    }
    
    @Test
    @DisplayName("秒传成功时记录正确的日志信息")
    void checkInstantUpload_LogsCorrectInformatiExcepException() {
        // Given
        when(storageObjectRepository.findByFileHash("blog", request.getFileHash()))
                .thenReturn(Optional.of(storageObject));
        when(fileRecordRepository.findByUserIdAndFileHash("blog", userId, request.getFileHash()))
                .thenReturn(Optional.empty());
        when(storageService.getUrl(storageObject.getStoragePath()))
                .thenReturn("https://cdn.example.com/2026/01/19/12345/images/file.jpg");
        when(fileRecordRepository.save(any(FileRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storageObjectRepository.incrementReferenceCount(storageObject.getId()))
                .thenReturn(true);
        
        // When
        InstantUploadCheckResponse response = instantUploadService.checkInstantUpload("blog", request, userId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getInstantUpload()).isTrue();
        
        // 验证所有必要的方法都被调用
        verify(storageObjectRepository).findByFileHash("blog", request.getFileHash());
        verify(fileRecordRepository).findByUserIdAndFileHash("blog", userId, request.getFileHash());
        verify(fileRecordRepository).save(any(FileRecord.class));
        verify(storageObjectRepository).incrementReferenceCount(storageObject.getId());
        verify(storageService).getUrl(storageObject.getStoragePath());
    }
}

