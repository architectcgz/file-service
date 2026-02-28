package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.FileDetailResponse;
import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.common.exception.FileNotFoundException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.infrastructure.cache.FileRedisKeys;
import com.architectcgz.file.infrastructure.config.CacheProperties;
import com.architectcgz.file.infrastructure.config.S3Properties;
import com.architectcgz.file.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

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
    private final RedisTemplate<String, String> redisTemplate;
    private final CacheProperties cacheProperties;
    
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
        String cachedUrl = getCachedUrl(fileId);
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

        // 统一访问控制检查（租户隔离 + 文件状态 + 访问级别）
        if (!canAccessFile(file, requestUserId, appId)) {
            // 区分异常类型：已删除文件返回 FileNotFoundException，其他返回 AccessDeniedException
            if (file.isDeleted()) {
                throw FileNotFoundException.deleted(fileId);
            }
            if (!file.belongsToApp(appId)) {
                throw new AccessDeniedException("文件不属于该应用");
            }
            throw new AccessDeniedException("无权访问该文件: " + fileId);
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
            cacheUrl(fileId, url);
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
     * 从缓存获取文件 URL
     * 
     * @param fileId 文件ID
     * @return 缓存的 URL，如果不存在返回 null
     */
    private String getCachedUrl(String fileId) {
        if (!cacheProperties.isEnabled()) {
            return null;
        }
        
        try {
            String cacheKey = FileRedisKeys.fileUrl(fileId);
            String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedUrl != null) {
                log.debug("Cache hit: fileId={}", fileId);
                return cachedUrl;
            }
            
            log.debug("Cache miss: fileId={}", fileId);
            return null;
        } catch (Exception e) {
            log.warn("Failed to get cached URL, fallback to database: fileId={}", fileId, e);
            return null;
        }
    }
    
    /**
     * 将文件 URL 写入缓存
     * 
     * @param fileId 文件ID
     * @param url 文件访问URL
     */
    private void cacheUrl(String fileId, String url) {
        if (!cacheProperties.isEnabled()) {
            return;
        }
        
        try {
            String cacheKey = FileRedisKeys.fileUrl(fileId);
            long ttl = cacheProperties.getUrl().getTtl();
            
            redisTemplate.opsForValue().set(cacheKey, url, ttl, TimeUnit.SECONDS);
            log.debug("Cached URL: fileId={}, ttl={}s", fileId, ttl);
        } catch (Exception e) {
            log.warn("Failed to cache URL: fileId={}", fileId, e);
            // 缓存失败不影响业务流程
        }
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
        if (!canAccessFile(file, requestUserId, appId)) {
            if (file.isDeleted()) {
                throw FileNotFoundException.deleted(fileId);
            }
            if (!file.belongsToApp(appId)) {
                throw new AccessDeniedException("文件不属于该应用");
            }
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
     * 完整的访问控制检查链：租户隔离 -> 文件状态 -> 访问级别
     * 传入已查询的FileRecord对象，避免重复查询
     *
     * @param file 文件记录
     * @param requestUserId 请求用户ID
     * @param appId 应用ID，用于租户隔离校验
     * @return 是否有权访问
     */
    public boolean canAccessFile(FileRecord file, String requestUserId, String appId) {
        // 1. 租户隔离：文件必须属于当前应用
        if (!file.belongsToApp(appId)) {
            log.warn("跨租户访问被拒绝: fileId={}, fileAppId={}, requestAppId={}",
                    file.getId(), file.getAppId(), appId);
            return false;
        }

        // 2. 文件状态：已删除的文件不可访问
        if (file.isDeleted()) {
            log.debug("已删除文件被拒绝访问: fileId={}", file.getId());
            return false;
        }

        // 3. 访问级别：公开文件任何人都可以访问
        if (file.getAccessLevel() == AccessLevel.PUBLIC) {
            return true;
        }

        // 4. 私有文件只有所有者可以访问
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
        
        // 更新访问级别
        boolean updated = fileRecordRepository.updateAccessLevel(fileId, newLevel);
        if (!updated) {
            throw new BusinessException("更新文件访问级别失败: " + fileId);
        }
        
        log.info("File access level updated: fileId={}, oldLevel={}, newLevel={}, userId={}", 
                fileId, file.getAccessLevel(), newLevel, requestUserId);
    }
}
