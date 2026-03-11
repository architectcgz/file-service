package com.architectcgz.file.application.service;

import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.application.dto.InitUploadRequest;
import com.architectcgz.file.application.dto.InitUploadResponse;
import com.architectcgz.file.application.dto.UploadProgressResponse;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.domain.service.TenantDomainService;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import com.architectcgz.file.infrastructure.repository.po.UploadPartPO;
import com.architectcgz.file.infrastructure.cache.UploadRedisKeys;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 分片上传应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadService {

    private static final AccessLevel DEFAULT_MULTIPART_UPLOAD_ACCESS_LEVEL = AccessLevel.PUBLIC;

    /**
     * 释放分布式锁的 Lua 脚本
     * 先比较锁 value，相同才删除，防止误删其他请求持有的锁
     */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final S3StorageService s3StorageService;
    private final UploadTaskRepository uploadTaskRepository;
    private final UploadPartRepository uploadPartRepository;
    private final FileRecordRepository fileRecordRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final MultipartProperties multipartProperties;
    private final FileTypeValidator fileTypeValidator;
    private final UploadPartMapper uploadPartMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final UploadPartTransactionHelper uploadPartTransactionHelper;
    private final TenantDomainService tenantDomainService;
    private final UploadTransactionHelper uploadTransactionHelper;
    
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

        String targetBucketName = resolveUploadBucketName();
        tenantDomainService.checkQuota(appId, request.getFileSize());
        
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
        String uploadId = s3StorageService.createMultipartUpload(storagePath, request.getContentType(), targetBucketName);
        log.info("Created S3 multipart upload: {}", uploadId);
        
        // 计算分片信息
        int chunkSize = multipartProperties.getChunkSize();
        int totalParts = (int) Math.ceil((double) request.getFileSize() / chunkSize);
        
        if (totalParts > multipartProperties.getMaxParts()) {
            throw new BusinessException(String.format(FileServiceErrorMessages.PART_COUNT_EXCEEDED, multipartProperties.getMaxParts()));
        }
        
        // 创建上传任务记录
        UploadTask task = new UploadTask();
        task.setId(UuidCreator.getTimeOrderedEpoch().toString());
        task.setAppId(appId);
        task.setUserId(userId);
        task.setFileName(request.getFileName());
        task.setFileSize(request.getFileSize());
        task.setFileHash(request.getFileHash());
        task.setContentType(request.getContentType());
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
     * <p>此方法不加 @Transactional：方法内部包含分布式锁获取/释放、
     * Thread.sleep 轮询等待、S3 网络调用等耗时操作，
     * 若持有数据库事务会长时间占用连接池资源，导致连接耗尽。
     * 数据库写操作已封装到 {@link UploadPartTransactionHelper#savePart} 中以独立短事务执行。
     *
     * @param taskId     任务 ID
     * @param partNumber 分片号（1-based）
     * @param data       分片数据
     * @param userId     用户 ID
     * @return ETag
     */
    public String uploadPart(String taskId, int partNumber, byte[] data, String userId) {
        log.info("Uploading part {} for task: {}", partNumber, taskId);

        // 查询任务
        UploadTask task = uploadTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(FileServiceErrorMessages.UPLOAD_TASK_NOT_FOUND));

        // 验证用户权限
        if (!task.getUserId().equals(userId)) {
            throw new AccessDeniedException(FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK);
        }

        // 验证任务状态
        if (task.getStatus() != UploadTaskStatus.UPLOADING) {
            throw new BusinessException(String.format(FileServiceErrorMessages.TASK_STATUS_INVALID, task.getStatus()));
        }

        // 验证任务是否过期
        if (task.getExpiresAt().isBefore(LocalDateTime.now())) {
            uploadTaskRepository.updateStatus(taskId, UploadTaskStatus.EXPIRED);
            throw new BusinessException(FileServiceErrorMessages.UPLOAD_TASK_EXPIRED);
        }

        // 验证分片号
        if (partNumber < 1 || partNumber > task.getTotalParts()) {
            throw new BusinessException(String.format(FileServiceErrorMessages.PART_NUMBER_INVALID, partNumber));
        }

        // 分布式锁：防止同一分片并发上传导致 ETag 不一致
        // 锁 value 使用 UUID，释放时通过 Lua 脚本比较后删除，防止误删其他请求持有的锁
        String lockKey = UploadRedisKeys.partLock(taskId, partNumber);
        String lockValue = UUID.randomUUID().toString();
        Duration lockTimeout = Duration.ofSeconds(multipartProperties.getLockTimeoutSeconds());
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, lockTimeout);
        if (!Boolean.TRUE.equals(locked)) {
            // 未获取到锁，说明有另一个请求正在上传同一分片，等待后查询 ETag 返回
            log.info("Part upload lock contention, waiting for existing upload: taskId={}, partNumber={}",
                    taskId, partNumber);
            return waitAndGetExistingEtag(taskId, partNumber);
        }

        try {
            return doUploadPart(task, taskId, partNumber, data);
        } finally {
            // 使用 Lua 脚本原子性地比较并删除锁，防止锁超时后误删其他请求持有的新锁
            redisTemplate.execute(
                    new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                    List.of(lockKey),
                    lockValue
            );
        }
    }

    /**
     * 等待并发上传完成后获取已有 ETag
     *
     * <p>当分布式锁竞争失败时，说明同一分片正在被另一个请求上传。
     * 此处使用 Thread.sleep 轮询数据库，而非响应式/回调方案，原因如下：
     * <ul>
     *   <li>分片上传场景为低频并发，轮询开销可接受</li>
     *   <li>引入异步回调会大幅增加代码复杂度，与项目同步 Servlet 模型不符</li>
     *   <li>等待时间上限为 3s（6 次 × 500ms），不会长时间阻塞线程</li>
     * </ul>
     * 如未来切换到响应式框架（WebFlux），可改为 Mono.delay + retry。
     *
     * @param taskId     任务 ID
     * @param partNumber 分片编号
     * @return 已有分片的 ETag
     */
    private String waitAndGetExistingEtag(String taskId, int partNumber) {
        int maxRetries = 6;
        for (int i = 0; i < maxRetries; i++) {
            try {
                // 每次等待 500ms，最多等待 3s，让持锁请求完成 S3 上传并写入数据库
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
     * <p>S3 上传完成后，通过 {@link UploadPartTransactionHelper#savePart} 以独立短事务写入数据库，
     * 避免在持有分布式锁期间同时持有数据库事务。
     *
     * @param task       上传任务
     * @param taskId     任务 ID
     * @param partNumber 分片编号
     * @param data       分片数据
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

        // 上传分片到 S3（网络 IO，不在事务内执行）
        String etag = s3StorageService.uploadPart(
                task.getStoragePath(),
                task.getUploadId(),
                partNumber,
                data,
                resolveUploadBucketName()
        );
        log.info("Uploaded part {} with ETag: {}", partNumber, etag);

        // 通过独立短事务保存分片记录，避免长事务占用连接池
        uploadPartTransactionHelper.savePart(taskId, partNumber, etag, (long) data.length);

        return etag;
    }
    
    /**
     * 完成分片上传
     * 
     * @param taskId 任务 ID
     * @param userId 用户 ID
     * @return 文件记录 ID
     */
    public String completeUpload(String taskId, String userId) {
        log.info("Completing multipart upload for task: {}", taskId);
        String targetBucketName = resolveUploadBucketName();
        
        // 查询任务
        UploadTask task = uploadTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(FileServiceErrorMessages.UPLOAD_TASK_NOT_FOUND));

        // 验证用户权限
        if (!task.getUserId().equals(userId)) {
            throw new AccessDeniedException(FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK);
        }

        // 验证任务状态
        if (task.getStatus() != UploadTaskStatus.UPLOADING) {
            throw new BusinessException(String.format(FileServiceErrorMessages.TASK_STATUS_INVALID, task.getStatus()));
        }
        
        // 验证所有分片是否已上传（使用 Bitmap 优化）
        int completedCount = uploadPartRepository.countCompletedParts(taskId);
        if (completedCount != task.getTotalParts()) {
            throw new BusinessException(String.format(FileServiceErrorMessages.PARTS_INCOMPLETE,
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
        s3StorageService.completeMultipartUpload(task.getStoragePath(), task.getUploadId(), completedParts, targetBucketName);
        log.info("Completed S3 multipart upload for task: {}", taskId);

        String fileHash = requireFileHash(task);
        Optional<StorageObject> existingStorageObject = storageObjectRepository.findByFileHashAndBucket(
                task.getAppId(),
                fileHash,
                targetBucketName
        );

        if (existingStorageObject.isPresent()) {
            StorageObject storageObject = existingStorageObject.get();
            FileRecord fileRecord = buildFileRecord(task, userId, storageObject.getId(), fileHash,
                    storageObject.getStoragePath(), storageObject.getFileSize(), storageObject.getContentType());

            try {
                uploadTransactionHelper.saveCompletedInstantUpload(task, storageObject.getId(), fileRecord);
            } catch (Exception dbEx) {
                cleanupS3Quietly(task.getStoragePath(), targetBucketName);
                throw dbEx;
            }

            cleanupS3Quietly(task.getStoragePath(), targetBucketName);
            log.info("Multipart upload dedup hit: taskId={}, fileId={}, storageObjectId={}",
                    taskId, fileRecord.getId(), storageObject.getId());
            return fileRecord.getId();
        }

        StorageObject storageObject = buildStorageObject(task, fileHash, targetBucketName);
        FileRecord fileRecord = buildFileRecord(
                task,
                userId,
                storageObject.getId(),
                fileHash,
                task.getStoragePath(),
                task.getFileSize(),
                task.getContentType()
        );

        try {
            uploadTransactionHelper.saveCompletedUpload(task, storageObject, fileRecord);
        } catch (Exception dbEx) {
            cleanupS3Quietly(task.getStoragePath(), targetBucketName);
            throw dbEx;
        }

        log.info("Multipart upload metadata persisted: taskId={}, fileId={}, storageObjectId={}",
                taskId, fileRecord.getId(), storageObject.getId());
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
                .orElseThrow(() -> new BusinessException(FileServiceErrorMessages.UPLOAD_TASK_NOT_FOUND));

        // 验证用户权限
        if (!task.getUserId().equals(userId)) {
            throw new AccessDeniedException(FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK);
        }
        
        // 验证任务状态
        if (task.getStatus() != UploadTaskStatus.UPLOADING) {
            log.warn("Task {} is not in UPLOADING status: {}", taskId, task.getStatus());
            return;
        }
        
        // 中止 S3 Multipart Upload
        try {
            s3StorageService.abortMultipartUpload(
                    task.getStoragePath(),
                    task.getUploadId(),
                    resolveUploadBucketName()
            );
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
                .orElseThrow(() -> new BusinessException(FileServiceErrorMessages.UPLOAD_TASK_NOT_FOUND));

        // 验证用户权限
        if (!task.getUserId().equals(userId)) {
            throw new AccessDeniedException(FileServiceErrorMessages.ACCESS_DENIED_VIEW_UPLOAD_TASK);
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

    private StorageObject buildStorageObject(UploadTask task, String fileHash, String bucketName) {
        return StorageObject.builder()
                .id(UuidCreator.getTimeOrderedEpoch().toString())
                .appId(task.getAppId())
                .fileHash(fileHash)
                .hashAlgorithm("MD5")
                .storagePath(task.getStoragePath())
                .bucketName(bucketName)
                .fileSize(task.getFileSize())
                .contentType(task.getContentType())
                .referenceCount(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private FileRecord buildFileRecord(UploadTask task, String userId, String storageObjectId, String fileHash,
                                       String storagePath, long fileSize, String contentType) {
        return FileRecord.builder()
                .id(UuidCreator.getTimeOrderedEpoch().toString())
                .appId(task.getAppId())
                .userId(userId)
                .storageObjectId(storageObjectId)
                .originalFilename(task.getFileName())
                .storagePath(storagePath)
                .fileSize(fileSize)
                .contentType(contentType)
                .fileHash(fileHash)
                .hashAlgorithm("MD5")
                .accessLevel(AccessLevel.PUBLIC)
                .status(FileStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private String requireFileHash(UploadTask task) {
        if (task.getFileHash() == null || task.getFileHash().isBlank()) {
            throw new BusinessException("上传任务缺少 fileHash，无法建立存储对象");
        }
        return task.getFileHash();
    }

    private String resolveUploadBucketName() {
        return s3StorageService.getBucketName(DEFAULT_MULTIPART_UPLOAD_ACCESS_LEVEL);
    }

    private void cleanupS3Quietly(String storagePath, String bucketName) {
        try {
            s3StorageService.delete(bucketName, storagePath);
            log.warn("分片合并后已清理临时 S3 对象: bucket={}, path={}", bucketName, storagePath);
        } catch (Exception cleanupEx) {
            log.error("分片合并后 S3 清理失败: bucket={}, path={}", bucketName, storagePath, cleanupEx);
        }
    }
}

