package com.architectcgz.file.application.service;

import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.application.dto.ConfirmUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadRequest;
import com.architectcgz.file.application.dto.PresignedUploadResponse;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.storage.ObjectMetadata;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import com.architectcgz.file.infrastructure.storage.StorageService;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 预签名 URL 服务
 * 支持客户端直接上传文件到 S3，无需通过服务器中转
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresignedUrlService {
    
    private final S3StorageService s3StorageService;
    private final StorageService storageService;
    private final StorageObjectRepository storageObjectRepository;
    private final FileRecordRepository fileRecordRepository;
    private final FileTypeValidator fileTypeValidator;
    
    /**
     * 预签名 URL 过期时间（秒）
     * 默认 15 分钟
     */
    @Value("${storage.access.presigned-url-expire-seconds:900}")
    private int presignedUrlExpireSeconds;
    
    /**
     * 获取预签名上传URL
     * 允许客户端直接上传文件到 S3
     * 
     * @param appId 应用ID
     * @param request 预签名上传请求
     * @param userId 用户 ID
     * @return 预签名上传响应
     */
    public PresignedUploadResponse getPresignedUploadUrl(String appId, PresignedUploadRequest request, String userId) {
        log.info("Generating presigned upload URL: userId={}, fileName={}, fileSize={}, fileHash={}", 
                userId, request.getFileName(), request.getFileSize(), request.getFileHash());
        
        // 验证文件类型
        fileTypeValidator.validateFile(
                request.getFileName(),
                request.getContentType(),
                request.getFileSize()
        );
        
        // 1. 检查是否存在相同 hash 的文件（秒传）
        Optional<StorageObject> existingStorageObject = storageObjectRepository.findByFileHash(appId, request.getFileHash());
        if (existingStorageObject.isPresent()) {
            // 文件已存在，检查用户是否已有该文件
            Optional<FileRecord> existingFileRecord = fileRecordRepository.findByUserIdAndFileHash(
                    appId, userId, request.getFileHash());
            
            if (existingFileRecord.isPresent() && !existingFileRecord.get().isDeleted()) {
                // 用户已有该文件，直接返回已存在的文件信息
                log.info("File already exists for user (instant upload): userId={}, fileHash={}", 
                        userId, request.getFileHash());
                throw new BusinessException("文件已存在，无需重复上传");
            }
        }
        
        // 2. 生成存储路径
        String extension = getExtension(request.getFileName());
        String storagePath = generateStoragePath(appId, userId, extension);
        
        // 3. 生成预签名上传 URL
        String presignedUrl = s3StorageService.generatePresignedPutUrl(
                storagePath, 
                request.getContentType(), 
                presignedUrlExpireSeconds
        );
        
        // 4. 计算过期时间
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(presignedUrlExpireSeconds);
        
        // 5. 构建响应
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", request.getContentType());
        
        log.info("Presigned upload URL generated: userId={}, storagePath={}, expiresAt={}", 
                userId, storagePath, expiresAt);
        
        return PresignedUploadResponse.builder()
                .presignedUrl(presignedUrl)
                .storagePath(storagePath)
                .expiresAt(expiresAt)
                .method("PUT")
                .headers(headers)
                .build();
    }
    
    /**
     * 确认上传完成
     * 客户端使用预签名 URL 上传完成后，调用此接口创建文件记录
     * 
     * @param request 确认上传请求
     * @param userId 用户 ID
     * @return 文件记录 ID 和访问 URL
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> confirmUpload(String appId, ConfirmUploadRequest request, String userId) {
        log.info("Confirming upload: userId={}, storagePath={}, fileHash={}", 
                userId, request.getStoragePath(), request.getFileHash());
        
        // 1. 通过 HeadObject 验证文件存在并获取真实元数据（fileSize、contentType）
        ObjectMetadata metadata = storageService.getObjectMetadata(request.getStoragePath());
        long realFileSize = metadata.getFileSize();
        String realContentType = metadata.getContentType();

        log.info("Got object metadata from storage: storagePath={}, fileSize={}, contentType={}",
                request.getStoragePath(), realFileSize, realContentType);

        // 2. 检查是否存在相同 hash 的 StorageObject（去重）
        Optional<StorageObject> existingStorageObject = storageObjectRepository.findByFileHash(appId, request.getFileHash());
        
        String storageObjectId;
        String fileUrl;
        
        if (existingStorageObject.isPresent()) {
            // 文件已存在，增加引用计数（去重）
            StorageObject storageObject = existingStorageObject.get();
            storageObjectRepository.incrementReferenceCount(storageObject.getId());
            storageObjectId = storageObject.getId();
            fileUrl = storageService.getUrl(storageObject.getStoragePath());
            
            log.info("File deduplication: fileHash={}, storageObjectId={}, referenceCount={}", 
                    request.getFileHash(), storageObjectId, storageObject.getReferenceCount() + 1);
        } else {
            // 新文件，创建 StorageObject，使用从 HeadObject 获取的真实元数据
            StorageObject storageObject = StorageObject.builder()
                    .id(generateFileId())
                    .appId(request.getAppId())
                    .fileHash(request.getFileHash())
                    .hashAlgorithm("MD5")
                    .storagePath(request.getStoragePath())
                    .fileSize(realFileSize)
                    .contentType(realContentType)
                    .referenceCount(1)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            storageObjectRepository.save(storageObject);
            storageObjectId = storageObject.getId();
            fileUrl = storageService.getUrl(request.getStoragePath());
            
            log.info("New StorageObject created: storageObjectId={}, storagePath={}", 
                    storageObjectId, request.getStoragePath());
        }
        
        // 3. 创建 FileRecord
        String fileRecordId = generateFileId();
        FileRecord fileRecord = FileRecord.builder()
                .id(fileRecordId)
                .appId(request.getAppId())
                .userId(userId)
                .storageObjectId(storageObjectId)
                .originalFilename(request.getOriginalFilename())
                .storagePath(request.getStoragePath())
                .fileSize(realFileSize)
                .contentType(realContentType)
                .fileHash(request.getFileHash())
                .hashAlgorithm("MD5")
                .status(FileStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        fileRecordRepository.save(fileRecord);
        
        log.info("Upload confirmed: fileRecordId={}, userId={}, storagePath={}", 
                fileRecordId, userId, request.getStoragePath());
        
        Map<String, String> result = new HashMap<>();
        result.put("fileId", fileRecordId);
        result.put("url", fileUrl);
        
        return result;
    }
    
    /**
     * 生成文件ID (使用UUIDv7 - 时间有序的UUID)
     */
    private String generateFileId() {
        return UuidCreator.getTimeOrderedEpoch().toString();
    }
    
    /**
     * 获取文件扩展名
     */
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
    
    /**
     * 生成存储路径
     * 格式: {year}/{month}/{day}/{userId}/{uuid}.{ext}
     * 
     * @param userId 用户ID
     * @param extension 文件扩展名
     * @return 存储路径
     */
    private String generateStoragePath(String appId, String userId, String extension) {
        LocalDateTime now = LocalDateTime.now();
        String fileId = generateFileId();
        
        return String.format("%s/%d/%02d/%02d/%s/files/%s.%s",
                appId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                userId,
                fileId,
                extension);
    }
}
