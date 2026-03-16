package com.platform.fileservice.core.ports.repository;

import com.platform.fileservice.core.domain.model.BlobObject;

import java.util.Optional;

/**
 * Repository port for physical blob objects.
 */
public interface BlobObjectRepository {

    Optional<BlobObject> findById(String blobObjectId);

    Optional<BlobObject> findByHash(String tenantId, String hashValue, String bucketName);

    BlobObject save(BlobObject blobObject);

    boolean incrementReferenceCount(String blobObjectId);
}
