package com.architectcgz.file.application.service.multipart.command;

import com.architectcgz.file.application.service.UploadPartTransactionHelper;
import com.architectcgz.file.application.service.multipart.storage.MultipartUploadStorageService;
import com.architectcgz.file.application.service.multipart.validator.MultipartUploadTaskValidator;
import com.architectcgz.file.application.service.uploadpart.query.UploadPartStateQueryService;
import com.architectcgz.file.application.service.uploadtask.command.UploadTaskCommandService;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.infrastructure.cache.UploadRedisKeys;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartPartUploadCommandService {

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final MultipartUploadTaskValidator multipartUploadTaskValidator;
    private final MultipartUploadStorageService multipartUploadStorageService;
    private final UploadTaskQueryService uploadTaskQueryService;
    private final UploadTaskCommandService uploadTaskCommandService;
    private final UploadPartStateQueryService uploadPartStateQueryService;
    private final RedisTemplate<String, String> redisTemplate;
    private final MultipartProperties multipartProperties;
    private final UploadPartTransactionHelper uploadPartTransactionHelper;

    public String uploadPart(String appId, String taskId, int partNumber, byte[] data, String userId) {
        log.info("Uploading part {} for task: {}", partNumber, taskId);

        UploadTask task = uploadTaskQueryService.getById(taskId);

        multipartUploadTaskValidator.validateTaskAccess(task, appId, userId, FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK);

        if (task.getStatus() != UploadTaskStatus.UPLOADING) {
            throw new BusinessException(
                    FileServiceErrorCodes.TASK_STATUS_INVALID,
                    String.format(FileServiceErrorMessages.TASK_STATUS_INVALID, task.getStatus())
            );
        }
        if (task.getExpiresAt().isBefore(LocalDateTime.now())) {
            uploadTaskCommandService.markExpired(taskId);
            throw new BusinessException(
                    FileServiceErrorCodes.UPLOAD_TASK_EXPIRED,
                    FileServiceErrorMessages.UPLOAD_TASK_EXPIRED
            );
        }
        if (partNumber < 1 || partNumber > task.getTotalParts()) {
            throw new BusinessException(
                    FileServiceErrorCodes.PART_NUMBER_INVALID,
                    String.format(FileServiceErrorMessages.PART_NUMBER_INVALID, partNumber)
            );
        }

        String lockKey = UploadRedisKeys.partLock(taskId, partNumber);
        String lockValue = UUID.randomUUID().toString();
        Duration lockTimeout = Duration.ofSeconds(multipartProperties.getLockTimeoutSeconds());
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, lockTimeout);
        if (!Boolean.TRUE.equals(locked)) {
            return waitAndGetExistingEtag(taskId, partNumber);
        }

        try {
            return doUploadPart(task, taskId, partNumber, data);
        } finally {
            redisTemplate.execute(
                    new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                    List.of(lockKey),
                    lockValue
            );
        }
    }

    private String waitAndGetExistingEtag(String taskId, int partNumber) {
        int maxRetries = 6;
        for (int i = 0; i < maxRetries; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(
                        FileServiceErrorCodes.WAIT_PART_UPLOAD_INTERRUPTED,
                        FileServiceErrorMessages.WAIT_PART_UPLOAD_INTERRUPTED
                );
            }
            Optional<UploadPart> existing = uploadPartStateQueryService.findUploadedPart(taskId, partNumber);
            if (existing.isPresent() && existing.get().getEtag() != null) {
                return existing.get().getEtag();
            }
        }
        throw new BusinessException(
                FileServiceErrorCodes.WAIT_PART_UPLOAD_TIMEOUT,
                FileServiceErrorMessages.WAIT_PART_UPLOAD_TIMEOUT
        );
    }

    private String doUploadPart(UploadTask task, String taskId, int partNumber, byte[] data) {
        Optional<UploadPart> existingPart = uploadPartStateQueryService.findUploadedPart(taskId, partNumber);
        if (existingPart.isPresent() && existingPart.get().getEtag() != null) {
            return existingPart.get().getEtag();
        }

        String etag = multipartUploadStorageService.uploadPart(
                task.getStoragePath(),
                task.getUploadId(),
                partNumber,
                data,
                multipartUploadStorageService.resolveUploadBucketName()
        );
        uploadPartTransactionHelper.savePart(taskId, partNumber, etag, (long) data.length);
        return etag;
    }
}
