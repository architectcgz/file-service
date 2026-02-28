package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.DirectUploadInitRequest;
import com.architectcgz.file.application.dto.DirectUploadInitResponse;
import com.architectcgz.file.application.dto.DirectUploadPartUrlRequest;
import com.architectcgz.file.application.dto.DirectUploadPartUrlResponse;
import com.architectcgz.file.application.dto.DirectUploadCompleteRequest;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import com.architectcgz.file.infrastructure.config.MultipartProperties;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * S3 直传服务
 * 
 * 提供客户端直接上传到 S3 的功能，减轻服务器带宽压力
 * 支持断点续传和分片上传
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectUploadService {
    
    private final S3StorageService s3StorageService;
    private final UploadTaskRepository uploadTaskRepository;
    private final UploadPartRepository uploadPartRepository;
    private final FileRecordRepository fileRecordRepository;
    private final MultipartProperties multipartProperties;
    private final FileTypeValidator fileTypeValidator;
    
    /**
     * 初始化直传上传
     * 
     * 支持秒传和断点续传：
     * 1. 如果文件已存在（基于 fileHash），直接返回文件ID和URL（秒传）
     * 2. 如果存在未完成的上传任务（基于 fileHash），返回已有任务信息和已完成分片列表（断点续传）
     * 3. 如果不存在或已过期，创建新任务
     * 
     * @param appId 应用ID
     * @param request 初始化请求
     * @param userId 用户ID
     * @return 初始化响应（包含 uploadId 和任务信息，或文件ID和URL）
     */
    @Transactional
    public DirectUploadInitResponse initDirectUpload(String appId, DirectUploadInitRequest request, String userId) {
        log.info("初始化直传上传: appId={}, userId={}, fileName={}, fileSize={}, fileHash={}", 
                appId, userId, request.getFileName(), request.getFileSize(), request.getFileHash());
        
        // 验证文件类型
        fileTypeValidator.validateFile(
                request.getFileName(),
                request.getContentType(),
                request.getFileSize()
        );
        
        // 1. 秒传检查：查询是否存在已完成的文件（基于 fileHash）
        if (request.getFileHash() != null && !request.getFileHash().isEmpty()) {
            log.info("检查秒传: appId={}, fileHash={}", appId, request.getFileHash());
            
            // 直接查询应用下是否存在已完成的文件（不区分用户）
            Optional<FileRecord> existingFile = fileRecordRepository
                    .findCompletedByAppIdAndFileHash(appId, request.getFileHash());
            
            if (existingFile.isPresent()) {
                FileRecord fileRecord = existingFile.get();
                
                log.info("秒传命中: fileId={}, fileHash={}, 原上传用户={}, 当前用户={}", 
                        fileRecord.getId(), request.getFileHash(), fileRecord.getUserId(), userId);
                
                // 生成文件访问URL（公开文件）
                String fileUrl = s3StorageService.getPublicUrl(fileRecord.getStoragePath());
                
                // 返回秒传响应
                return DirectUploadInitResponse.builder()
                        .isInstantUpload(true)
                        .fileId(fileRecord.getId())
                        .fileUrl(fileUrl)
                        .build();
            }
        }
        
        // 2. 断点续传检查：查询是否存在未完成的上传任务（基于 fileHash）
        if (request.getFileHash() != null && !request.getFileHash().isEmpty()) {
            log.info("查询断点续传任务: appId={}, userId={}, fileHash={}", appId, userId, request.getFileHash());
            Optional<UploadTask> existingTask = uploadTaskRepository
                    .findByUserIdAndFileHash(appId, userId, request.getFileHash());
            
            log.info("查询结果: existingTask.isPresent()={}", existingTask.isPresent());
            
            if (existingTask.isPresent()) {
                UploadTask task = existingTask.get();
                
                // 检查任务是否过期
                if (task.getExpiresAt().isBefore(LocalDateTime.now())) {
                    log.info("上传任务已过期，中止旧任务并创建新任务: taskId={}, expiresAt={}", 
                            task.getId(), task.getExpiresAt());
                    
                    // 中止旧的 S3 upload
                    try {
                        s3StorageService.abortMultipartUpload(task.getStoragePath(), task.getUploadId());
                    } catch (Exception e) {
                        log.warn("中止过期任务失败（可能已被清理）: taskId={}, error={}", 
                                task.getId(), e.getMessage());
                    }
                    
                    // 更新任务状态为已中止
                    uploadTaskRepository.updateStatus(task.getId(), UploadTaskStatus.ABORTED);
                    
                    // 继续创建新任务
                } else if (task.canResume()) {
                    // 任务未过期且可以续传
                    
                    // 验证文件大小是否一致
                    if (!request.getFileSize().equals(task.getFileSize())) {
                        log.warn("文件大小不匹配，无法续传: taskId={}, expected={}, actual={}", 
                                task.getId(), task.getFileSize(), request.getFileSize());
                        throw new BusinessException(FileServiceErrorMessages.FILE_SIZE_MISMATCH);
                    }
                    
                    // 从 S3 查询已上传分片的完整信息（包括 ETag）
                    List<S3StorageService.PartInfo> s3PartInfos = s3StorageService.listUploadedPartsWithETag(
                            task.getStoragePath(), 
                            task.getUploadId()
                    );
                    
                    // 提取分片编号列表
                    List<Integer> completedParts = s3PartInfos.stream()
                            .map(S3StorageService.PartInfo::getPartNumber)
                            .collect(Collectors.toList());
                    
                    // 转换为响应格式
                    List<DirectUploadInitResponse.PartInfo> completedPartInfos = s3PartInfos.stream()
                            .map(info -> DirectUploadInitResponse.PartInfo.builder()
                                    .partNumber(info.getPartNumber())
                                    .etag(info.getEtag())
                                    .build())
                            .collect(Collectors.toList());
                    
                    log.info("恢复上传任务: taskId={}, completedParts={}/{}", 
                            task.getId(), completedParts.size(), task.getTotalParts());
                    
                    // 返回已有任务信息（包括已完成分片的 ETag）
                    return DirectUploadInitResponse.builder()
                            .taskId(task.getId())
                            .uploadId(task.getUploadId())
                            .storagePath(task.getStoragePath())
                            .chunkSize(task.getChunkSize())
                            .totalParts(task.getTotalParts())
                            .completedParts(completedParts)
                            .completedPartInfos(completedPartInfos)
                            .isResume(true)
                            .isInstantUpload(false)
                            .build();
                }
            }
        }
        
        // 3. 创建新任务
        
        // 生成存储路径
        String storagePath = generateStoragePath(appId, userId, request.getFileName());
        
        // 创建 S3 Multipart Upload
        String uploadId = s3StorageService.createMultipartUpload(storagePath, request.getContentType());
        log.info("创建 S3 multipart upload: uploadId={}", uploadId);
        
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
        task.setStoragePath(storagePath);
        task.setUploadId(uploadId);
        task.setTotalParts(totalParts);
        task.setChunkSize(chunkSize);
        task.setStatus(UploadTaskStatus.UPLOADING);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setExpiresAt(LocalDateTime.now().plusHours(multipartProperties.getTaskExpireHours()));
        
        uploadTaskRepository.save(task);
        log.info("创建上传任务: taskId={}", task.getId());
        
        return DirectUploadInitResponse.builder()
                .taskId(task.getId())
                .uploadId(uploadId)
                .storagePath(storagePath)
                .chunkSize(chunkSize)
                .totalParts(totalParts)
                .completedParts(Collections.emptyList())
                .completedPartInfos(Collections.emptyList())
                .isResume(false)
                .isInstantUpload(false)
                .build();
    }
    
    /**
     * 获取分片上传的预签名 URL
     * 
     * 客户端使用此 URL 直接上传分片到 S3
     * 
     * @param request 请求参数
     * @param userId 用户ID
     * @return 预签名 URL 列表
     */
    public DirectUploadPartUrlResponse getPartUploadUrls(DirectUploadPartUrlRequest request, String userId) {
        log.info("获取分片上传URL: taskId={}, partNumbers={}", 
                request.getTaskId(), request.getPartNumbers());
        
        // 查询上传任务
        UploadTask task = uploadTaskRepository.findById(request.getTaskId())
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
            throw new BusinessException(FileServiceErrorMessages.UPLOAD_TASK_EXPIRED);
        }
        
        // 生成预签名 URL
        List<DirectUploadPartUrlResponse.PartUrl> partUrls = new ArrayList<>();
        
        for (Integer partNumber : request.getPartNumbers()) {
            // 验证分片号
            if (partNumber < 1 || partNumber > task.getTotalParts()) {
                throw new BusinessException(String.format(FileServiceErrorMessages.PART_NUMBER_INVALID, partNumber));
            }
            
            // 生成预签名 URL（有效期 1 小时）
            String presignedUrl = s3StorageService.generatePresignedUploadPartUrl(
                    task.getStoragePath(),
                    task.getUploadId(),
                    partNumber,
                    3600  // 1 小时
            );
            
            partUrls.add(DirectUploadPartUrlResponse.PartUrl.builder()
                    .partNumber(partNumber)
                    .uploadUrl(presignedUrl)
                    .expiresIn(3600)
                    .build());
        }
        
        log.info("生成分片上传URL: taskId={}, count={}", request.getTaskId(), partUrls.size());
        
        return DirectUploadPartUrlResponse.builder()
                .taskId(task.getId())
                .partUrls(partUrls)
                .build();
    }
    
    /**
     * 完成直传上传
     * 
     * 1. 验证所有分片已上传
     * 2. 调用 S3 complete multipart upload
     * 3. 创建文件记录
     * 
     * @param request 完成请求
     * @param userId 用户ID
     * @return 文件记录ID
     */
    @Transactional
    public String completeDirectUpload(DirectUploadCompleteRequest request, String userId) {
        log.info("完成直传上传: taskId={}, parts={}", request.getTaskId(), request.getParts().size());
        
        // 查询上传任务
        UploadTask task = uploadTaskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new BusinessException(FileServiceErrorMessages.UPLOAD_TASK_NOT_FOUND));

        // 验证用户权限
        if (!task.getUserId().equals(userId)) {
            throw new AccessDeniedException(FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK);
        }

        // 验证任务状态
        if (task.getStatus() != UploadTaskStatus.UPLOADING) {
            throw new BusinessException(String.format(FileServiceErrorMessages.TASK_STATUS_INVALID, task.getStatus()));
        }
        
        // 验证分片数量
        if (request.getParts().size() != task.getTotalParts()) {
            throw new BusinessException(String.format(FileServiceErrorMessages.PART_COUNT_MISMATCH,
                    task.getTotalParts(), request.getParts().size()));
        }
        
        // 构建 CompletedPart 列表
        List<CompletedPart> completedParts = request.getParts().stream()
                .sorted((p1, p2) -> Integer.compare(p1.getPartNumber(), p2.getPartNumber()))
                .map(part -> CompletedPart.builder()
                        .partNumber(part.getPartNumber())
                        .eTag(part.getEtag())
                        .build())
                .collect(Collectors.toList());
        
        // 保存分片信息到数据库（用于断点续传）
        List<UploadPart> uploadParts = request.getParts().stream()
                .map(part -> UploadPart.builder()
                        .id(UuidCreator.getTimeOrderedEpoch().toString())
                        .taskId(task.getId())
                        .partNumber(part.getPartNumber())
                        .etag(part.getEtag())
                        .uploadedAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());
        
        // 使用 syncAllPartsToDatabase 批量保存
        uploadPartRepository.syncAllPartsToDatabase(task.getId(), uploadParts);
        log.info("保存分片信息: taskId={}, count={}", task.getId(), uploadParts.size());
        
        // 完成 S3 Multipart Upload
        s3StorageService.completeMultipartUpload(
                task.getStoragePath(), 
                task.getUploadId(), 
                completedParts
        );
        log.info("完成 S3 multipart upload: taskId={}", request.getTaskId());
        
        // 创建文件记录
        FileRecord fileRecord = new FileRecord();
        fileRecord.setId(UuidCreator.getTimeOrderedEpoch().toString());
        fileRecord.setAppId(task.getAppId());
        fileRecord.setUserId(userId);
        fileRecord.setOriginalFilename(task.getFileName());
        fileRecord.setStoragePath(task.getStoragePath());
        fileRecord.setFileSize(task.getFileSize());
        fileRecord.setContentType(request.getContentType());
        fileRecord.setFileHash(task.getFileHash());
        fileRecord.setHashAlgorithm("MD5");
        fileRecord.setStatus(FileStatus.COMPLETED);
        fileRecord.setCreatedAt(LocalDateTime.now());
        fileRecord.setUpdatedAt(LocalDateTime.now());
        
        fileRecordRepository.save(fileRecord);
        log.info("创建文件记录: fileId={}", fileRecord.getId());
        
        // 更新任务状态
        task.setStatus(UploadTaskStatus.COMPLETED);
        task.setUpdatedAt(LocalDateTime.now());
        uploadTaskRepository.updateStatus(request.getTaskId(), UploadTaskStatus.COMPLETED);
        
        return fileRecord.getId();
    }
    
    /**
     * 生成存储路径
     * 
     * @param appId 应用ID
     * @param userId 用户ID
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
