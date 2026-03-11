package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.InstantUploadCheckRequest;
import com.architectcgz.file.application.dto.InstantUploadCheckResponse;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.storage.StorageService;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 秒传服务
 * 通过文件哈希值检查文件是否已存在，实现秒传功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstantUploadService {

    private static final AccessLevel DEFAULT_INSTANT_UPLOAD_ACCESS_LEVEL = AccessLevel.PUBLIC;
    
    private final StorageObjectRepository storageObjectRepository;
    private final FileRecordRepository fileRecordRepository;
    private final StorageService storageService;
    
    /**
     * 检查文件是否可以秒传
     * 
     * @param appId 应用ID
     * @param request 秒传检查请求（包含文件哈希、文件名等信息）
     * @param userId 用户ID
     * @return 秒传检查响应
     */
    @Transactional(rollbackFor = Exception.class)
    public InstantUploadCheckResponse checkInstantUpload(String appId, InstantUploadCheckRequest request, String userId) {
        log.info("Checking instant upload: appId={}, fileHash={}, userId={}, fileName={}", 
                appId, request.getFileHash(), userId, request.getFileName());
        
        // 1. 检查用户是否已有相同哈希的文件
        Optional<FileRecord> existingFileRecord = fileRecordRepository.findByUserIdAndFileHash(
                appId, userId, request.getFileHash());
        
        if (existingFileRecord.isPresent() && !existingFileRecord.get().isDeleted()) {
            // 用户已有该文件，直接返回已存在的文件信息
            FileRecord fileRecord = existingFileRecord.get();
            String fileUrl = resolveFileUrl(fileRecord.getStorageObjectId(), fileRecord.getStoragePath());
            
            log.info("Instant upload: user already has file with same hash: userId={}, fileHash={}, fileId={}", 
                    userId, request.getFileHash(), fileRecord.getId());
            
            return InstantUploadCheckResponse.builder()
                    .instantUpload(true)
                    .needUpload(false)
                    .fileId(fileRecord.getId())
                    .url(fileUrl)
                    .build();
        }
        
        // 2. 检查是否存在相同哈希的 StorageObject（其他用户上传过）
        String targetBucketName = storageService.getBucketName(DEFAULT_INSTANT_UPLOAD_ACCESS_LEVEL);
        Optional<StorageObject> existingStorageObject = StringUtils.hasText(targetBucketName)
                ? storageObjectRepository.findByFileHashAndBucket(appId, request.getFileHash(), targetBucketName)
                : storageObjectRepository.findByFileHash(appId, request.getFileHash());
        
        if (existingStorageObject.isPresent()) {
            // 文件已存在，创建新的 FileRecord 并增加引用计数
            StorageObject storageObject = existingStorageObject.get();
            storageObjectRepository.incrementReferenceCount(storageObject.getId());
            
            // 创建新的 FileRecord
            String fileRecordId = generateFileId();
            FileRecord fileRecord = FileRecord.builder()
                    .id(fileRecordId)
                    .appId(appId)
                    .userId(userId)
                    .storageObjectId(storageObject.getId())
                    .originalFilename(request.getFileName())
                    .storagePath(storageObject.getStoragePath())
                    .fileSize(storageObject.getFileSize())
                    .contentType(storageObject.getContentType())
                    .fileHash(request.getFileHash())
                    .hashAlgorithm("MD5")
                    .status(FileStatus.COMPLETED)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            fileRecordRepository.save(fileRecord);
            
            String fileUrl = storageService.getPublicUrl(storageObject.getBucketName(), storageObject.getStoragePath());
            
            log.info("Instant upload successful: fileHash={}, userId={}, fileId={}, storageObjectId={}", 
                    request.getFileHash(), userId, fileRecordId, storageObject.getId());
            
            return InstantUploadCheckResponse.builder()
                    .instantUpload(true)
                    .needUpload(false)
                    .fileId(fileRecordId)
                    .url(fileUrl)
                    .build();
        }
        
        // 3. 文件不存在，需要正常上传
        log.info("File not found for instant upload: fileHash={}, userId={}", request.getFileHash(), userId);
        
        return InstantUploadCheckResponse.builder()
                .instantUpload(false)
                .needUpload(true)
                .build();
    }
    
    /**
     * 生成文件ID (使用UUIDv7 - 时间有序的UUID)
     */
    private String generateFileId() {
        return UuidCreator.getTimeOrderedEpoch().toString();
    }

    private String resolveFileUrl(String storageObjectId, String storagePath) {
        return storageObjectRepository.findById(storageObjectId)
                .map(storageObject -> storageService.getPublicUrl(storageObject.getBucketName(), storageObject.getStoragePath()))
                .orElseGet(() -> storageService.getPublicUrl(storagePath));
    }
}
