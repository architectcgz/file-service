package com.architectcgz.file.infrastructure.scheduler;

import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 上传任务清理定时任务测试
 */
@ExtendWith(MockitoExtension.class)
class UploadTaskCleanupSchedulerTest {
    
    @Mock
    private UploadTaskRepository uploadTaskRepository;
    
    @Mock
    private S3StorageService s3StorageService;
    
    @Mock
    private MultipartProperties multipartProperties;
    
    @InjectMocks
    private UploadTaskCleanupScheduler scheduler;
    
    private UploadTask expiredTask1;
    private UploadTask expiredTask2;
    
    @BeforeEach
    void setUp() {
        expiredTask1 = new UploadTask();
        expiredTask1.setId("task-1");
        expiredTask1.setUserId("1");
        expiredTask1.setFileName("file1.mp4");
        expiredTask1.setStoragePath("2026/01/18/1/file1.mp4");
        expiredTask1.setUploadId("upload-id-1");
        expiredTask1.setStatus(UploadTaskStatus.UPLOADING);
        expiredTask1.setExpiresAt(LocalDateTime.now().minusHours(1));
        
        expiredTask2 = new UploadTask();
        expiredTask2.setId("task-2");
        expiredTask2.setUserId("2");
        expiredTask2.setFileName("file2.mp4");
        expiredTask2.setStoragePath("2026/01/18/2/file2.mp4");
        expiredTask2.setUploadId("upload-id-2");
        expiredTask2.setStatus(UploadTaskStatus.UPLOADING);
        expiredTask2.setExpiresAt(LocalDateTime.now().minusHours(2));
    }
    
    @Test
    void testCleanupExpiredTasks_NoExpiredTasks() {
        // Given
        when(uploadTaskRepository.findExpiredTasks(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        
        // When
        scheduler.cleanupExpiredTasks();
        
        // Then
        verify(uploadTaskRepository).findExpiredTasks(any(LocalDateTime.class));
        verify(s3StorageService, never()).abortMultipartUpload(anyString(), anyString());
        verify(uploadTaskRepository, never()).updateStatus(anyString(), any(UploadTaskStatus.class));
    }
    
    @Test
    void testCleanupExpiredTasks_Success() {
        // Given
        List<UploadTask> expiredTasks = Arrays.asList(expiredTask1, expiredTask2);
        when(uploadTaskRepository.findExpiredTasks(any(LocalDateTime.class)))
                .thenReturn(expiredTasks);
        
        // When
        scheduler.cleanupExpiredTasks();
        
        // Then
        verify(uploadTaskRepository).findExpiredTasks(any(LocalDateTime.class));
        
        // Verify S3 cleanup for both tasks
        verify(s3StorageService).abortMultipartUpload(
                eq(expiredTask1.getStoragePath()), 
                eq(expiredTask1.getUploadId())
        );
        verify(s3StorageService).abortMultipartUpload(
                eq(expiredTask2.getStoragePath()), 
                eq(expiredTask2.getUploadId())
        );
        
        // Verify status update for both tasks
        verify(uploadTaskRepository).updateStatus(
                eq(expiredTask1.getId()), 
                eq(UploadTaskStatus.EXPIRED)
        );
        verify(uploadTaskRepository).updateStatus(
                eq(expiredTask2.getId()), 
                eq(UploadTaskStatus.EXPIRED)
        );
    }
    
    @Test
    void testCleanupExpiredTasks_S3AbortFails() {
        // Given
        List<UploadTask> expiredTasks = Collections.singletonList(expiredTask1);
        when(uploadTaskRepository.findExpiredTasks(any(LocalDateTime.class)))
                .thenReturn(expiredTasks);
        
        // S3 abort fails but should not prevent status update
        doThrow(new RuntimeException("S3 error"))
                .when(s3StorageService)
                .abortMultipartUpload(anyString(), anyString());
        
        // When
        scheduler.cleanupExpiredTasks();
        
        // Then
        verify(s3StorageService).abortMultipartUpload(
                eq(expiredTask1.getStoragePath()), 
                eq(expiredTask1.getUploadId())
        );
        
        // Status should still be updated despite S3 failure
        verify(uploadTaskRepository).updateStatus(
                eq(expiredTask1.getId()), 
                eq(UploadTaskStatus.EXPIRED)
        );
    }
    
    @Test
    void testCleanupExpiredTasks_StatusUpdateFails() {
        // Given
        List<UploadTask> expiredTasks = Collections.singletonList(expiredTask1);
        when(uploadTaskRepository.findExpiredTasks(any(LocalDateTime.class)))
                .thenReturn(expiredTasks);
        
        // Status update fails
        doThrow(new RuntimeException("Database error"))
                .when(uploadTaskRepository)
                .updateStatus(anyString(), any(UploadTaskStatus.class));
        
        // When
        scheduler.cleanupExpiredTasks();
        
        // Then
        verify(s3StorageService).abortMultipartUpload(
                eq(expiredTask1.getStoragePath()), 
                eq(expiredTask1.getUploadId())
        );
        verify(uploadTaskRepository).updateStatus(
                eq(expiredTask1.getId()), 
                eq(UploadTaskStatus.EXPIRED)
        );
        
        // Scheduler should continue despite failure (logged but not thrown)
    }
    
    @Test
    void testCleanupExpiredTasks_PartialFailure() {
        // Given
        List<UploadTask> expiredTasks = Arrays.asList(expiredTask1, expiredTask2);
        when(uploadTaskRepository.findExpiredTasks(any(LocalDateTime.class)))
                .thenReturn(expiredTasks);
        
        // First task succeeds, second task fails on status update
        doNothing()
                .when(uploadTaskRepository)
                .updateStatus(eq(expiredTask1.getId()), any(UploadTaskStatus.class));
        
        doThrow(new RuntimeException("Database error"))
                .when(uploadTaskRepository)
                .updateStatus(eq(expiredTask2.getId()), any(UploadTaskStatus.class));
        
        // When
        scheduler.cleanupExpiredTasks();
        
        // Then
        // Both tasks should be attempted
        verify(s3StorageService).abortMultipartUpload(
                eq(expiredTask1.getStoragePath()), 
                eq(expiredTask1.getUploadId())
        );
        verify(s3StorageService).abortMultipartUpload(
                eq(expiredTask2.getStoragePath()), 
                eq(expiredTask2.getUploadId())
        );
        
        verify(uploadTaskRepository).updateStatus(
                eq(expiredTask1.getId()), 
                eq(UploadTaskStatus.EXPIRED)
        );
        verify(uploadTaskRepository).updateStatus(
                eq(expiredTask2.getId()), 
                eq(UploadTaskStatus.EXPIRED)
        );
    }
    
    @Test
    void testCleanupExpiredTasks_RepositoryQueryFails() {
        // Given
        when(uploadTaskRepository.findExpiredTasks(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("Database connection error"));
        
        // When
        scheduler.cleanupExpiredTasks();
        
        // Then
        verify(uploadTaskRepository).findExpiredTasks(any(LocalDateTime.class));
        
        // Should not proceed to cleanup if query fails
        verify(s3StorageService, never()).abortMultipartUpload(anyString(), anyString());
        verify(uploadTaskRepository, never()).updateStatus(anyString(), any(UploadTaskStatus.class));
    }
}
