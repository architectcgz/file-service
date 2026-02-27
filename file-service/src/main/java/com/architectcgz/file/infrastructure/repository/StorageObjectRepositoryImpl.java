package com.architectcgz.file.infrastructure.repository;

import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.repository.mapper.StorageObjectMapper;
import com.architectcgz.file.infrastructure.repository.po.StorageObjectPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 存储对象仓储实现
 * 
 * @author Blog Team
 */
@Repository
@RequiredArgsConstructor
public class StorageObjectRepositoryImpl implements StorageObjectRepository {
    
    private final StorageObjectMapper storageObjectMapper;
    
    @Override
    public StorageObject save(StorageObject storageObject) {
        StorageObjectPO po = toPO(storageObject);
        storageObjectMapper.insert(po);
        return storageObject;
    }
    
    @Override
    public Optional<StorageObject> findByFileHash(String appId, String fileHash) {
        StorageObjectPO po = storageObjectMapper.selectByFileHash(appId, fileHash);
        return Optional.ofNullable(toDomain(po));
    }
    
    @Override
    public Optional<StorageObject> findById(String id) {
        StorageObjectPO po = storageObjectMapper.selectById(id);
        return Optional.ofNullable(toDomain(po));
    }
    
    @Override
    public boolean incrementReferenceCount(String id) {
        int rows = storageObjectMapper.incrementReferenceCount(id, LocalDateTime.now());
        return rows > 0;
    }
    
    @Override
    public boolean decrementReferenceCount(String id) {
        int rows = storageObjectMapper.decrementReferenceCount(id, LocalDateTime.now());
        return rows > 0;
    }
    
    @Override
    public boolean deleteById(String id) {
        int rows = storageObjectMapper.deleteById(id);
        return rows > 0;
    }
    
    /**
     * 将领域模型转换为持久化对或
     */
    private StorageObjectPO toPO(StorageObject storageObject) {
        if (storageObject == null) {
            return null;
        }
        
        StorageObjectPO po = new StorageObjectPO();
        po.setId(storageObject.getId());
        po.setAppId(storageObject.getAppId());
        po.setFileHash(storageObject.getFileHash());
        po.setHashAlgorithm(storageObject.getHashAlgorithm());
        po.setStoragePath(storageObject.getStoragePath());
        po.setFileSize(storageObject.getFileSize());
        po.setContentType(storageObject.getContentType());
        po.setReferenceCount(storageObject.getReferenceCount());
        po.setCreatedAt(storageObject.getCreatedAt());
        po.setUpdatedAt(storageObject.getUpdatedAt());
        
        return po;
    }
    
    /**
     * 将持久化对象转换为领域模或
     */
    private StorageObject toDomain(StorageObjectPO po) {
        if (po == null) {
            return null;
        }
        
        return StorageObject.builder()
                .id(po.getId())
                .appId(po.getAppId())
                .fileHash(po.getFileHash())
                .hashAlgorithm(po.getHashAlgorithm())
                .storagePath(po.getStoragePath())
                .fileSize(po.getFileSize())
                .contentType(po.getContentType())
                .referenceCount(po.getReferenceCount())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
