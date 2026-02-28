package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.FileDetailResponse;
import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.common.exception.FileNotFoundException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.infrastructure.cache.FileUrlCacheManager;
import com.architectcgz.file.infrastructure.config.S3Properties;
import com.architectcgz.file.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 文件访问服务
 * 负责根据文件访问级别生成正确的访问URL，并验证用户访问权限
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAccessService {
    
    private final FileRecordRepository fileRecordRepository;
    private final StorageService storageService;
    private final S3Properties s3Properties;
    private final FileUrlCacheManager fileUrlCacheManager;
    
    @Value("${storage.access.private-url-expire-seconds:3600}")
    private int privateUrlExpireSeconds;
    
    /**
     * 获取文件访问URL（带appId参数）
     * 根据文件的访问级别返回正确的URL：
     * - 公开文件：返回直接访问URL（可能是CDN URL）
     * - 私有文件：验证权限后返回预签名URL
     * 
     * @param appId 应用ID
     * @param fileId 文件ID
     * @param requestUserId 请求用户ID
     * @return 文件URL响应
     * @throws BusinessException 如果文件不存在或用户无权访问
     */
    public FileUrlResponse getFileUrl(String appId, String fileId, String requestUserId) {
        // 1. 尝试从缓存获取
        // 注意：缓存中只存储公开文件的URL，私有文件的预签名URL不缓存
        // 因为预签名URL有时效性，缓存会导致URL过期后仍然返回旧URL
        String cachedUrl = fileUrlCacheManager.get(fileId);
        if (cachedUrl != null) {
            // 缓存命中，直接返回（缓存中只有公开文件的URL）
            log.debug("Returning cached URL for fileId={}", fileId);
            return FileUrlResponse.builder()
                    .url(cachedUrl)
                    .permanent(true)
                    .expiresAt(null)
                    .build();
        }
        
        // 2. 缓存未命中，查询数据库
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> FileNotFoundException.notFound(fileId));
        
        // 验证文件属于该应用
        if (!file.belongsToApp(appId)) {
            throw new AccessDeniedException("文件不属于该应用");
        }
        
        // 验证文件未被删除
        if (file.getStatus() == com.architectcgz.file.domain.model.FileStatus.DELETED) {
            throw FileNotFoundException.deleted(fileId);
        }
        
        String url;
        boolean isPermanent;
        LocalDateTime expiresAt;
        
        if (file.getAccessLevel() == AccessLevel.PUBLIC) {
            // 公开文件：返回公开URL
            url = storageService.getPublicUrl(file.getStoragePath());
            isPermanent = true;
            expiresAt = null;
            
            // 将公开文件的URL写入缓存
            fileUrlCacheManager.put(fileId, url);
        } else {
            // 私有文件：验证权限后返回预签名URL（不缓存）
            if (!canAccessFile(file, requestUserId)) {
                throw new AccessDeniedException("无权访问该文件: " + fileId);
            }
            url = storageService.generatePresignedUrl(
                    file.getStoragePath(), 
                    Duration.ofSeconds(privateUrlExpireSeconds)
            );
            isPermanent = false;
            expiresAt = LocalDateTime.now().plusSeconds(privateUrlExpireSeconds);
        }
        
        return FileUrlResponse.builder()
                .url(url)
                .permanent(isPermanent)
                .expiresAt(expiresAt)
                .build();
    }
    
    
    /**
     * 获取文件详情
     * 
     * @param appId 应用ID
     * @param fileId 文件ID
     * @param requestUserId 请求用户ID
     * @return 文件详情响应
     * @throws BusinessException 如果文件不存在或用户无权访问
     */
    public FileDetailResponse getFileDetail(String appId, String fileId, String requestUserId) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> FileNotFoundException.notFound(fileId));
        
        // 验证文件属于该应用
        if (!file.belongsToApp(appId)) {
            throw new AccessDeniedException("文件不属于该应用");
        }
        
        // 验证文件未被删除
        if (file.getStatus() == com.architectcgz.file.domain.model.FileStatus.DELETED) {
            throw FileNotFoundException.deleted(fileId);
        }
        
        // 对于私有文件，验证访问权限
        if (file.getAccessLevel() == AccessLevel.PRIVATE && !canAccessFile(file, requestUserId)) {
            throw new AccessDeniedException("无权访问该文件: " + fileId);
        }
        
        return FileDetailResponse.builder()
                .fileId(file.getId())
                .userId(file.getUserId())
                .originalFilename(file.getOriginalFilename())
                .fileSize(file.getFileSize())
                .contentType(file.getContentType())
                .fileHash(file.getFileHash())
                .hashAlgorithm(file.getHashAlgorithm())
                .accessLevel(file.getAccessLevel())
                .status(file.getStatus())
                .createdAt(file.getCreatedAt())
                .updatedAt(file.getUpdatedAt())
                .build();
    }
    
    /**
     * 验证用户是否有权访问文件
     * 传入已查询的FileRecord对象，避免重复查询
     * 
     * @param file 文件记录
     * @param requestUserId 请求用户ID
     * @return 是否有权访问
     */
    public boolean canAccessFile(FileRecord file, String requestUserId) {
        // 公开文件任何人都可以访问
        if (file.getAccessLevel() == AccessLevel.PUBLIC) {
            return true;
        }
        
        // 私有文件只有所有者可以访问
        if (file.getAccessLevel() == AccessLevel.PRIVATE) {
            return file.getUserId() != null && file.getUserId().equals(requestUserId);
        }
        
        return false;
    }
    
    /**
     * 更新文件访问级别
     *
     * @param appId 应用ID
     * @param fileId 文件ID
     * @param requestUserId 请求用户ID
     * @param newLevel 新的访问级别
     * @throws BusinessException 如果文件不存在或用户无权修改
     */
    @Transactional
    public void updateAccessLevel(String appId, String fileId, String requestUserId, AccessLevel newLevel) {
        FileRecord file = fileRecordRepository.findById(fileId)
                .orElseThrow(() -> FileNotFoundException.notFound(fileId));

        // 验证文件属于该应用
        if (!file.belongsToApp(appId)) {
            throw new AccessDeniedException("文件不属于该应用");
        }

        // 只有文件所有者可以修改访问级别
        if (!file.getUserId().equals(requestUserId)) {
            throw new AccessDeniedException("无权修改该文件的访问级别: " + fileId);
        }

        // 级别未变更时跳过，避免无意义的数据库写和缓存操作
        if (file.getAccessLevel() == newLevel) {
            log.debug("Access level unchanged, skip update: fileId={}, level={}", fileId, newLevel);
            return;
        }

        // 更新访问级别
        boolean updated = fileRecordRepository.updateAccessLevel(fileId, newLevel);
        if (!updated) {
            throw new BusinessException("更新文件访问级别失败: " + fileId);
        }

        log.info("File access level updated: fileId={}, oldLevel={}, newLevel={}, userId={}",
                fileId, file.getAccessLevel(), newLevel, requestUserId);

        // 在事务提交后清除缓存，避免事务回滚后缓存已被清除的窗口期问题
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileUrlCacheManager.evict(fileId);
            }
        });
    }

}
