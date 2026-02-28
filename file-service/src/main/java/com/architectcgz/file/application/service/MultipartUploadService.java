package com.architectcgz.file.application.service;

import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.application.dto.InitUploadRequest;
import com.architectcgz.file.application.dto.InitUploadResponse;
import com.architectcgz.file.application.dto.UploadProgressResponse;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import com.architectcgz.file.infrastructure.repository.po.UploadPartPO;
import com.architectcgz.file.infrastructure.cache.UploadRedisKeys;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 分片上传应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadService {
    
    private final S3StorageService s3StorageService;
    private final UploadTaskRepository uploadTaskRepository;
    private final UploadPartRepository uploadPartRepository;
    private final FileRecordRepository fileRecordRepository;
    private final MultipartProperties multipartProperties;
    private final FileTypeValidator fileTypeValidator;
    private final UploadPartMapper uploadPartMapper;
    private final RedisTemplate<String, String> redisTemplate;
    
    /**
     * 初始化分片上传
     * 
     * @param appId 应用ID
     * @param request 初始化请求
     * @param userId 用户 ID
     * @return 初始化响应
     */
    @Transactional
    public InitUploadResponse initUpload(String appId, InitUploadRequest request, String userId) {
        log.info("Initializing multipart upload for user: {}, file: {}, size: {}", 
                userId, request.getFileName(), request.getFileSize());
        
        // 验证文件类型
        fileTypeValidator.validateFile(
                request.getFileName(),
                request.getContentType(),
                request.getFileSize()
        );
        
        // 检查断点续传：如果存在相同 fileHash 的未完成任务，返回已存在的任务
        if (request.getFileHash() != null && !request.getFileHash().isEmpty()) {
            Optional<UploadTask> existingTaskOpt = uploadTaskRepository.findByUserIdAndFileHash(appId, userId, request.getFileHash());
            if (existingTaskOpt.isPresent()) {
                UploadTask existingTask = existingTaskOpt.get();
                if (existingTask.getStatus() == UploadTaskStatus.UPLOADING) {
                    log.info("Found existing upload task: {}, returning for resume", existingTask.getId());
                    
                    // 查询已完成的分片编号（使用 Bitmap 优化）
                    List<Integer> completedPartNumbers = uploadPartRepository.findCompletedPartNumbers(existingTask.getId());
                    
                    return InitUploadResponse.builder()
                            .taskId(existingTask.getId())
                            .uploadId(existingTask.getUploadId())
                            .chunkSize(existingTask.getChunkSize())
                            .totalParts(existingTask.getTotalParts())
                            .completedParts(completedPartNumbers)
                            .build();
                }
            }
        }
        
        // 生成存储路径
        String storagePath = generateStoragePath(appId, userId, request.getFileName());
        
        // 创建 S3 Multipart Upload
        String uploadId = s3StorageService.createMultipartUpload(storagePath, request.getContentType());
        log.info("Created S3 multipart upload: {}", uploadId);
        
        // 计算分片信息
        int chunkSize = multipartProperties.getChunkSize();
        int totalParts = (int) Math.ceil((double) request.getFileSize() / chunkSize);
        
        if (totalParts > multipartProperties.getMaxParts()) {
            throw new BusinessException("文件过大，分片数超过限制: " + multipartProperties.getMaxParts());
        }
        
        // 创建上传任务记录
        UploadTask task = new UploadTask();
        task.setId(UuidCreator.getTimeOrderedEpoch().toString());
        task.setAppId(appId);
        task.setUserId(userId);
        task.setFileName(request.getFileName());
        task.setFileSize(request.getFileSize());
        task.setFileHash(request.getFileHash());
        task.setStoragePath(storagePath);
        task.setUploadId(uploadId);
        task.setTotalParts(totalParts);
        task.setChunkSize(chunkSize);
        task.setStatus(UploadTaskStatus.UPLOADING);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setExpiresAt(LocalDateTime.now().plusHours(multipartProperties.getTaskExpireHours()));
        
        uploadTaskRepository.save(task);
        log.info("Created upload task: {}", task.getId());
        
        return InitUploadResponse.builder()
                .taskId(task.getId())
                .uploadId(uploadId)
                .chunkSize(chunkSize)
                .totalParts(totalParts)
                .completedParts(new ArrayList<>())
                .build();
    }
    
    /**
     * 上传分片
     * 
     * @param taskId 任务 ID
     * @param partNumber 分片号(1-based)
     * @param data 分片数据
     * @param userId 用户 ID
     * @return ETag
     */
    @Transactional
    public String uploadPart(String taskId, int partNumber, byte[] data, String userId) {
        log.info("Uploading part {} for task: {}", partNumber, taskId);

        // 查询任务
        UploadTask task = uploadTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("上传任务不存在"));

        // 验证用户权限
        if (!task.getUserId().equals(userId)) {
            throw new AccessDeniedException("无权操作该上传任务");
        }

        // 验证任务状态
        if (task.getStatus() != UploadTaskStatus.UPLOADING) {
            throw new BusinessException("任务状态不正确: " + task.getStatus());
        }

        // 验证任务是否过期
        if (task.getExpiresAt().isBefore(LocalDateTime.now())) {
            task.setStatus(UploadTaskStatus.EXPIRED);
            uploadTaskRepository.updateStatus(taskId, UploadTaskStatus.EXPIRED);
            throw new BusinessException("上传任务已过期");
        }

        // 验证分片号
        if (partNumber < 1 || partNumber > task.getTotalParts()) {
            throw new BusinessException("分片号无效: " + partNumber);
        }

        // 分布式锁：防止同一分片并发上传导致 ETag 不一致
        String lockKey = UploadRedisKeys.partLock(taskId, partNumber);
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", PART_LOCK_TIMEOUT);
        if (!Boolean.TRUE.equals(locked)) {
            // 未获取到锁，说明有另一个请求正在上传同一分片，等待后查询 ETag 返回
            log.info("Part upload lock contention, waiting for existing upload: taskId={}, partNumber={}",
                    taskId, partNumber);
            return waitAndGetExistingEtag(taskId, partNumber);
        }

        try {
            return doUploadPart(task, taskId, partNumber, data);
        } finally {
            // 释放锁
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 分片锁超时时间（防止死锁）
     */
    private static final Duration PART_LOCK_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 等待并发上传完成后获取已有 ETag
     * 当分布式锁竞争失败时，轮询数据库获取已有分片的 ETag
     *
     * @param taskId 任务ID
     * @param partNumber 分片编号
     * @return 已有分片的 ETag
     */
    private String waitAndGetExistingEtag(String taskId, int partNumber) {
        // 短暂等待后查询数据库中的 ETag
        int maxRetries = 6;
        for (int i = 0; i < maxRetries; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("等待分片上传完成时被中断");
            }
            Optional<UploadPart> existing = uploadPartRepository.findByTaskIdAndPartNumber(taskId, partNumber);
            if (existing.isPresent() && existing.get().getEtag() != null) {
                log.info("Part idempotent hit after lock wait: taskId={}, partNumber={}", taskId, partNumber);
                return existing.get().getEtag();
            }
        }
        throw new BusinessException("等待分片上传完成超时，请重试");
    }

    /**
     * 执行实际的分片上传逻辑（已持有分布式锁）
     *
     * @param task 上传任务
     * @param taskId 任务ID
     * @param partNumber 分片编号
     * @param data 分片数据
     * @return ETag
     */
    private String doUploadPart(UploadTask task, String taskId, int partNumber, byte[] data) {
        // 检查分片是否已上传（使用 bitmap 优化）
        List<Integer> completedPartNumbers = uploadPartRepository.findCompletedPartNumbers(taskId);
        if (completedPartNumbers.contains(partNumber)) {
            // 幂等处理：查询已有分片的 ETag 并直接返回，避免重复上传到 S3
            Optional<UploadPart> existingPart = uploadPartRepository.findByTaskIdAndPartNumber(taskId, partNumber);
            if (existingPart.isPresent() && existingPart.get().getEtag() != null) {
                log.info("Part idempotent hit: taskId={}, partNumber={}", taskId, partNumber);
                log.debug("Part idempotent hit ETag detail: taskId={}, partNumber={}, etag={}",
                        taskId, partNumber, existingPart.get().getEtag());
                return existingPart.get().getEtag();
            }
            // 数据库中无记录或无 ETag（可能仅在 Bitmap 中），仍需上传
            log.info("Part in bitmap but no ETag in DB, re-uploading: taskId={}, partNumber={}",
                    taskId, partNumber);
        }

        // 上传分片到 S3
        String etag = s3StorageService.uploadPart(task.getStoragePath(), task.getUploadId(), partNumber, data);
        log.info("Uploaded part {} with ETag: {}", partNumber, etag);

        // 保存分片记录
        UploadPart part = new UploadPart();
        part.setId(UuidCreator.getTimeOrderedEpoch().toString());
        part.setTaskId(taskId);
        part.setPartNumber(partNumber);
        part.setEtag(etag);
        part.setSize((long) data.length);
        part.setUploadedAt(LocalDateTime.now());

        uploadPartRepository.savePart(part);

        return etag;
    }
    
    /**
     * 完成分片上传
     * 
     * @param taskId 任务 ID
     * @param userId 用户 ID
     * @return 文件记录 ID
     */
    @Transactional
    public String completeUpload(String taskId, String userId) {
        log.info("Completing multipart upload for task: {}", taskId);
        
        // 查询任务
        UploadTask task = uploadTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("上传任务不存在"));
        
        // 验证用户权限
        if (!task.getUserId().equals(userId)) {
            throw new AccessDeniedException("无权操作该上传任务");
        }
        
        // 验证任务状态
        if (task.getStatus() != UploadTaskStatus.UPLOADING) {
            throw new BusinessException("任务状态不正确: " + task.getStatus());
        }
        
        // 验证所有分片是否已上传（使用 Bitmap 优化）
        int completedCount = uploadPartRepository.countCompletedParts(taskId);
        if (completedCount != task.getTotalParts()) {
            throw new BusinessException(String.format("分片未全部上传，已上传:%d, 总数: %d", 
                    completedCount, task.getTotalParts()));
        }
        
        // 查询已完成的分片编号
        List<Integer> completedPartNumbers = uploadPartRepository.findCompletedPartNumbers(taskId);
        
        // 构建分片信息用于全量同步
        // 注意：这里需要 ETag 信息，但 Bitmap 中没有存储
        // 解决方案：从 S3 listParts 获取，或者在 savePart 时同步到数据库
        // 临时方案：先全量同步到数据库，再查询
        List<UploadPart> partsForSync = new ArrayList<>();
        // 这里需要从某处获取 ETag，暂时留空，由 syncAllPartsToDatabase 处理
        
        // 全量同步到数据库（会从数据库查询已有记录）
        uploadPartRepository.syncAllPartsToDatabase(taskId, partsForSync);
        
        // 从数据库查询完整的分片信息（包含 ETag）
        List<UploadPart> parts = new ArrayList<>();
        for (Integer partNumber : completedPartNumbers) {
            // 查询单个分片（需要添加新方法）
            // 暂时使用批量查询
        }
        
        // 使用 Mapper 直接查询（临时方案）
        List<UploadPartPO> partPOs = uploadPartMapper.selectByTaskId(taskId);
        
        // 构建 CompletedPart 列表
        List<CompletedPart> completedParts = partPOs.stream()
                .sorted((p1, p2) -> Integer.compare(p1.getPartNumber(), p2.getPartNumber()))
                .map(po -> CompletedPart.builder()
                        .partNumber(po.getPartNumber())
                        .eTag(po.getEtag())
                        .build())
                .collect(Collectors.toList());
        
        // 完成 S3 Multipart Upload
        s3StorageService.completeMultipartUpload(task.getStoragePath(), task.getUploadId(), completedParts);
        log.info("Completed S3 multipart upload for task: {}", taskId);
        
        // 创建文件记录
        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(UuidCreator.getTimeOrderedEpoch().toString());
        fileRecord.setAppId(task.getAppId());
        fileRecord.setUserId(userId);
        fileRecord.setOriginalFilename(task.getFileName());
        fileRecord.setStoragePath(task.getStoragePath());  // 使用任务中的存储路径
        fileRecord.setFileSize(task.getFileSize());
        fileRecord.setContentType("application/octet-stream"); // TODO: 从任务中获取
        fileRecord.setFileHash(task.getFileHash());
        fileRecord.setHashAlgorithm("MD5");
        fileRecord.setStatus(FileStatus.COMPLETED);
        fileRecord.setCreatedAt(LocalDateTime.now());
        fileRecord.setUpdatedAt(LocalDateTime.now());
        
        fileRecordRepository.save(fileRecord);
        log.info("Created file record: {}", fileRecord.getId());
        
        // 更新任务状态
        task.setStatus(UploadTaskStatus.COMPLETED);
        task.setUpdatedAt(LocalDateTime.now());
        uploadTaskRepository.updateStatus(taskId, UploadTaskStatus.COMPLETED);
        
        return fileRecord.getId();
    }
    
    /**
     * 中止分片上传
     * 
     * @param taskId 任务 ID
     * @param userId 用户 ID
     */
    @Transactional
    public void abortUpload(String taskId, String userId) {
        log.info("Aborting multipart upload for task: {}", taskId);
        
        // 查询任务
        UploadTask task = uploadTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("上传任务不存在"));
        
        // 验证用户权限
        if (!task.getUserId().equals(userId)) {
            throw new AccessDeniedException("无权操作该上传任务");
        }
        
        // 验证任务状态
        if (task.getStatus() != UploadTaskStatus.UPLOADING) {
            log.warn("Task {} is not in UPLOADING status: {}", taskId, task.getStatus());
            return;
        }
        
        // 中止 S3 Multipart Upload
        try {
            s3StorageService.abortMultipartUpload(task.getStoragePath(), task.getUploadId());
            log.info("Aborted S3 multipart upload for task: {}", taskId);
        } catch (Exception e) {
            log.error("Failed to abort S3 multipart upload for task: {}", taskId, e);
            // 继续更新任务状态，即使 S3 中止失败
        }
        
        // 更新任务状态
        task.setStatus(UploadTaskStatus.ABORTED);
        task.setUpdatedAt(LocalDateTime.now());
        uploadTaskRepository.updateStatus(taskId, UploadTaskStatus.ABORTED);
    }
    
    /**
     * 查询上传进度
     * 
     * @param taskId 任务 ID
     * @param userId 用户 ID
     * @return 上传进度
     */
    public UploadProgressResponse getProgress(String taskId, String userId) {
        // 查询任务
        UploadTask task = uploadTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("上传任务不存在"));
        
        // 验证用户权限
        if (!task.getUserId().equals(userId)) {
            throw new AccessDeniedException("无权查看该上传任务");
        }
        
        // 查询已完成的分片数量（使用 Bitmap 优化）
        int completedPartsCount = uploadPartRepository.countCompletedParts(taskId);
        
        // 计算已上传字节数（估算）
        long uploadedBytes = (long) completedPartsCount * task.getChunkSize();
        if (uploadedBytes > task.getFileSize()) {
            uploadedBytes = task.getFileSize();
        }
        
        // 计算进度百分比
        int percentage = (int) ((uploadedBytes * 100) / task.getFileSize());
        
        return UploadProgressResponse.builder()
                .taskId(task.getId())
                .totalParts(task.getTotalParts())
                .completedParts(completedPartsCount)
                .uploadedBytes(uploadedBytes)
                .totalBytes(task.getFileSize())
                .percentage(percentage)
                .build();
    }
    
    /**
     * 列出用户的上传任务
     * 
     * @param appId 应用ID
     * @param userId 用户 ID
     * @return 任务列表
     */
    public List<UploadTask> listTasks(String appId, String userId) {
        // 默认返回最近100个任务
        return uploadTaskRepository.findByUserId(appId, userId, 100);
    }
    
    /**
     * 生成存储路径
     * 
     * @param userId 用户 ID
     * @param fileName 文件名
     * @return 存储路径
     */
    private String generateStoragePath(String appId, String userId, String fileName) {
        LocalDateTime now = LocalDateTime.now();
        String fileId = UuidCreator.getTimeOrderedEpoch().toString();
        String extension = getExtension(fileName);
        
        return String.format("%s/%d/%02d/%02d/%s/files/%s.%s",
                appId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                userId,
                fileId,
                extension);
    }
    
    /**
     * 获取文件扩展名
     * 
     * @param fileName 文件名
     * @return 扩展名（不含点）
     */
    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "bin";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}

