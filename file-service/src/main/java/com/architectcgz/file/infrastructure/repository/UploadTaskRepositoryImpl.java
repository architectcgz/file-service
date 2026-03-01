package com.architectcgz.file.infrastructure.repository;

import com.architectcgz.file.domain.model.UploadPart;
import com.architectcgz.file.domain.model.UploadTask;
import com.architectcgz.file.domain.model.UploadTaskStatus;
import com.architectcgz.file.domain.repository.UploadPartRepository;
import com.architectcgz.file.domain.repository.UploadTaskRepository;
import com.architectcgz.file.infrastructure.repository.mapper.UploadPartMapper;
import com.architectcgz.file.infrastructure.repository.mapper.UploadTaskMapper;
import com.architectcgz.file.infrastructure.repository.po.UploadPartPO;
import com.architectcgz.file.infrastructure.repository.po.UploadTaskPO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 上传任务仓储实现
 * 
 * 职责：仅负责上传任务（UploadTask）的持久化操作
 * 不包含分片（UploadPart）相关操作，分片操作由 UploadPartRepository 负责
 * 
 * @author Blog Team
 */
@Repository
@RequiredArgsConstructor
public class UploadTaskRepositoryImpl implements UploadTaskRepository {
    
    private final UploadTaskMapper uploadTaskMapper;
    
    @Override
    public void save(UploadTask task) {
        UploadTaskPO po = toTaskPO(task);
        uploadTaskMapper.insert(po);
    }
    
    @Override
    public Optional<UploadTask> findById(String id) {
        UploadTaskPO po = uploadTaskMapper.selectById(id);
        return Optional.ofNullable(toTaskDomain(po));
    }
    
    @Override
    public Optional<UploadTask> findByUserIdAndFileHash(String appId, String userId, String fileHash) {
        UploadTaskPO po = uploadTaskMapper.selectByUserIdAndFileHash(appId, userId, fileHash);
        return Optional.ofNullable(toTaskDomain(po));
    }
    
    @Override
    public List<UploadTask> findExpiredTasks(LocalDateTime now) {
        List<UploadTaskPO> pos = uploadTaskMapper.selectExpiredTasks(now);
        return pos.stream()
                .map(this::toTaskDomain)
                .collect(Collectors.toList());
    }
    
    @Override
    public void updateStatus(String taskId, UploadTaskStatus status) {
        uploadTaskMapper.updateStatus(taskId, status.getCode(), LocalDateTime.now());
    }
    
    @Override
    public List<UploadTask> findByUserId(String appId, String userId, int limit) {
        List<UploadTaskPO> pos = uploadTaskMapper.selectByUserId(appId, userId, limit);
        return pos.stream()
                .map(this::toTaskDomain)
                .collect(Collectors.toList());
    }
    
    /**
     * 将上传任务领域模型转换为持久化对或
     */
    private UploadTaskPO toTaskPO(UploadTask task) {
        if (task == null) {
            return null;
        }
        
        UploadTaskPO po = new UploadTaskPO();
        po.setId(task.getId());
        po.setAppId(task.getAppId());
        po.setUserId(task.getUserId());
        po.setFileName(task.getFileName());
        po.setFileSize(task.getFileSize());
        po.setFileHash(task.getFileHash());
        po.setContentType(task.getContentType());
        po.setStoragePath(task.getStoragePath());
        po.setUploadId(task.getUploadId());
        po.setTotalParts(task.getTotalParts());
        po.setChunkSize(task.getChunkSize());
        po.setStatus(task.getStatus() != null ? task.getStatus().getCode() : UploadTaskStatus.UPLOADING.getCode());
        po.setCreatedAt(task.getCreatedAt());
        po.setUpdatedAt(task.getUpdatedAt());
        po.setExpiresAt(task.getExpiresAt());
        
        return po;
    }
    
    /**
     * 将持久化对象转换为上传任务领域模或
     */
    private UploadTask toTaskDomain(UploadTaskPO po) {
        if (po == null) {
            return null;
        }
        
        return UploadTask.builder()
                .id(po.getId())
                .appId(po.getAppId())
                .userId(po.getUserId())
                .fileName(po.getFileName())
                .fileSize(po.getFileSize())
                .fileHash(po.getFileHash())
                .contentType(po.getContentType())
                .storagePath(po.getStoragePath())
                .uploadId(po.getUploadId())
                .totalParts(po.getTotalParts())
                .chunkSize(po.getChunkSize())
                .status(UploadTaskStatus.fromCode(po.getStatus()))
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .expiresAt(po.getExpiresAt())
                .build();
    }
    
}
