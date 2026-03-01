package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.FileDetailResponse;
import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
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
        String cachedUrl = fileUrlCacheManager.get(fileId);
        if (cachedUrl != null) {
            // [H1] 缓存命中后仍需校验权限，防止跨租户访问和已删除文件绕过
            FileRecord file = fileRecordRepository.findById(fileId)
                    .orElseThrow(() -> FileNotFoundException.notFound(fileId));
            checkFileAccess(file, fileId, requestUserId, appId);

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

        // 统一访问控制检查（租户隔离 + 文件状态 + 访问级别）
        checkFileAccess(file, fileId, requestUserId, appId);

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
            // 私有文件：返回预签名URL（不缓存）
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

        // 统一访问控制检查（租户隔离 + 文件状态 + 访问级别）
        checkFileAccess(file, fileId, requestUserId, appId);
        
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
     * 校验文件访问权限，校验失败直接抛出对应异常
     * 完整的访问控制检查链：租户隔离 -> 文件状态 -> 访问级别
     *
     * @param file 文件记录
     * @param fileId 文件ID（用于异常消息）
     * @param requestUserId 请求用户ID
     * @param appId 应用ID，用于租户隔离校验
     * @throws FileNotFoundException 跨租户访问或文件已删除时抛出（不暴露文件存在性）
     * @throws AccessDeniedException 无权访问时抛出
     */
    private void checkFileAccess(FileRecord file, String fileId, String requestUserId, String appId) {
        // 1. 租户隔离：跨租户统一返回 404，不暴露文件存在性
        if (!file.belongsToApp(appId)) {
            log.warn("跨租户访问被拒绝: fileId={}, fileAppId={}, requestAppId={}",
                    file.getId(), file.getAppId(), appId);
            throw FileNotFoundException.notFound(fileId);
        }

        // 2. 文件状态：已删除文件返回 404
        if (file.isDeleted()) {
            log.debug("已删除文件被拒绝访问: fileId={}", file.getId());
            throw FileNotFoundException.deleted(fileId);
        }

        // 3. 访问级别：公开文件任何人都可以访问
        if (file.getAccessLevel() == AccessLevel.PUBLIC) {
            return;
        }

        // 4. 私有文件只有所有者可以访问
        if (file.getAccessLevel() == AccessLevel.PRIVATE) {
            if (file.getUserId() == null || !file.getUserId().equals(requestUserId)) {
                throw new AccessDeniedException(String.format(FileServiceErrorMessages.ACCESS_DENIED_FILE, fileId));
            }
            return;
        }

        // 未知访问级别，拒绝访问
        throw new AccessDeniedException(String.format(FileServiceErrorMessages.ACCESS_DENIED_FILE, fileId));
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

        // 统一访问控制检查（租户隔离 + 文件状态），复用 checkFileAccess
        checkFileAccess(file, fileId, requestUserId, appId);

        // 只有文件所有者可以修改访问级别，增加 null 防护
        if (file.getUserId() == null || !file.getUserId().equals(requestUserId)) {
            throw new AccessDeniedException(String.format(FileServiceErrorMessages.ACCESS_DENIED_UPDATE_ACCESS_LEVEL, fileId));
        }

        // 级别未变更时跳过，避免无意义的数据库写和缓存操作
        if (file.getAccessLevel() == newLevel) {
            log.debug("Access level unchanged, skip update: fileId={}, level={}", fileId, newLevel);
            return;
        }

        // 更新访问级别
        boolean updated = fileRecordRepository.updateAccessLevel(fileId, newLevel);
        if (!updated) {
            throw new BusinessException(String.format(FileServiceErrorMessages.UPDATE_ACCESS_LEVEL_FAILED, fileId));
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
