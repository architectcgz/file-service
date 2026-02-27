package com.architectcgz.file.infrastructure.repository;

import com.architectcgz.file.application.dto.FileQuery;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.infrastructure.repository.mapper.FileRecordMapper;
import com.architectcgz.file.infrastructure.repository.po.FileRecordPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文件记录仓储实现
 * 
 * @author Blog Team
 */
@Repository
@RequiredArgsConstructor
public class FileRecordRepositoryImpl implements FileRecordRepository {
    
    private final FileRecordMapper fileRecordMapper;
    
    @Override
    public FileRecord save(FileRecord fileRecord) {
        FileRecordPO po = toPO(fileRecord);
        fileRecordMapper.insert(po);
        return fileRecord;
    }
    
    @Override
    public Optional<FileRecord> findById(String id) {
        FileRecordPO po = fileRecordMapper.selectById(id);
        return Optional.ofNullable(toDomain(po));
    }
    
    @Override
    public Optional<FileRecord> findByUserIdAndFileHash(String appId, String userId, String fileHash) {
        FileRecordPO po = fileRecordMapper.selectByUserIdAndFileHash(appId, userId, fileHash);
        return Optional.ofNullable(toDomain(po));
    }
    
    @Override
    public Optional<FileRecord> findCompletedByAppIdAndFileHash(String appId, String fileHash) {
        FileRecordPO po = fileRecordMapper.selectCompletedByAppIdAndFileHash(appId, fileHash);
        return Optional.ofNullable(toDomain(po));
    }
    
    @Override
    public boolean updateStatus(String id, FileStatus status) {
        int rows = fileRecordMapper.updateStatus(id, status.name(), LocalDateTime.now());
        return rows > 0;
    }
    
    @Override
    public boolean updateAccessLevel(String id, AccessLevel accessLevel) {
        int rows = fileRecordMapper.updateAccessLevel(id, accessLevel.name().toLowerCase(), LocalDateTime.now());
        return rows > 0;
    }
    
    @Override
    public List<FileRecord> findByQuery(FileQuery query) {
        List<FileRecordPO> pos = fileRecordMapper.selectByQuery(query);
        return pos.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }
    
    @Override
    public long countByQuery(FileQuery query) {
        return fileRecordMapper.countByQuery(query);
    }
    
    @Override
    public boolean deleteById(String id) {
        int rows = fileRecordMapper.deleteById(id);
        return rows > 0;
    }
    
    /**
     * 将领域模型转换为持久化对或
     */
    private FileRecordPO toPO(FileRecord fileRecord) {
        if (fileRecord == null) {
            return null;
        }
        
        FileRecordPO po = new FileRecordPO();
        po.setId(fileRecord.getId());
        po.setAppId(fileRecord.getAppId());
        po.setUserId(fileRecord.getUserId());
        po.setStorageObjectId(fileRecord.getStorageObjectId());
        po.setOriginalFilename(fileRecord.getOriginalFilename());
        po.setStoragePath(fileRecord.getStoragePath());  // 设置存储路径
        po.setFileSize(fileRecord.getFileSize());
        po.setContentType(fileRecord.getContentType());
        po.setFileHash(fileRecord.getFileHash());
        po.setAccessLevel("public"); // 默认公开访问，后续任务会实现访问控制
        po.setStatus(fileRecord.getStatus() != null ? fileRecord.getStatus().name() : FileStatus.COMPLETED.name());
        po.setCreatedAt(fileRecord.getCreatedAt());
        po.setUpdatedAt(fileRecord.getUpdatedAt());
        
        return po;
    }
    
    /**
     * 将持久化对象转换为领域模或
     */
    private FileRecord toDomain(FileRecordPO po) {
        if (po == null) {
            return null;
        }
        
        return FileRecord.builder()
                .id(po.getId())
                .appId(po.getAppId())
                .userId(po.getUserId())
                .storageObjectId(po.getStorageObjectId())
                .originalFilename(po.getOriginalFilename())
                .storagePath(po.getStoragePath()) // 从数据库读取存储路径
                .fileSize(po.getFileSize())
                .contentType(po.getContentType())
                .fileHash(po.getFileHash())
                .hashAlgorithm("MD5") // 默认MD5，后续任务会实现
                .status(FileStatus.valueOf(po.getStatus()))
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
