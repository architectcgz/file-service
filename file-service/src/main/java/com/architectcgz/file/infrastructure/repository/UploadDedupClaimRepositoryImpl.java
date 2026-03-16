package com.architectcgz.file.infrastructure.repository;

import com.architectcgz.file.domain.repository.UploadDedupClaimRepository;
import com.architectcgz.file.infrastructure.repository.mapper.UploadDedupClaimMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 上传去重占位仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class UploadDedupClaimRepositoryImpl implements UploadDedupClaimRepository {

    private final UploadDedupClaimMapper uploadDedupClaimMapper;

    @Override
    public boolean tryAcquireClaim(String appId, String fileHash, String bucketName, String ownerToken, LocalDateTime expiresAt) {
        return uploadDedupClaimMapper.tryAcquireClaim(appId, fileHash, bucketName, ownerToken, expiresAt) > 0;
    }

    @Override
    public boolean renewClaim(String appId, String fileHash, String bucketName, String ownerToken, LocalDateTime expiresAt) {
        return uploadDedupClaimMapper.renewClaim(appId, fileHash, bucketName, ownerToken, expiresAt) > 0;
    }

    @Override
    public void releaseClaim(String appId, String fileHash, String bucketName, String ownerToken) {
        uploadDedupClaimMapper.releaseClaim(appId, fileHash, bucketName, ownerToken);
    }
}
