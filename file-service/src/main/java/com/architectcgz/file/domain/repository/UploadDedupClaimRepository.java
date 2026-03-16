package com.architectcgz.file.domain.repository;

import java.time.OffsetDateTime;

/**
 * 上传去重占位仓储。
 *
 * 使用短生命周期 claim 抢占同一 appId + fileHash + bucket 的上传资格，
 * 将真正的对象上传放到锁外执行，避免长时间串行阻塞。
 */
public interface UploadDedupClaimRepository {

    boolean tryAcquireClaim(String appId, String fileHash, String bucketName, String ownerToken, OffsetDateTime expiresAt);

    boolean renewClaim(String appId, String fileHash, String bucketName, String ownerToken, OffsetDateTime expiresAt);

    void releaseClaim(String appId, String fileHash, String bucketName, String ownerToken);
}
