package com.platform.fileservice.core.ports.storage;

import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.StoredObjectMetadata;
import com.platform.fileservice.core.domain.model.UploadedPart;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * Port for object storage URL resolution and upload integration.
 */
public interface ObjectStoragePort {

    URI resolveObjectUri(String bucketName, String objectKey);

    String resolveBucketName(AccessLevel accessLevel);

    String normalizeBucketName(String bucketName);

    String createMultipartUpload(String bucketName, String objectKey, String contentType);

    String generatePresignedPutObjectUrl(String bucketName,
                                         String objectKey,
                                         String contentType,
                                         Duration ttl);

    String generatePresignedUploadPartUrl(String bucketName,
                                          String objectKey,
                                          String uploadId,
                                          int partNumber,
                                          Duration ttl);

    String uploadPart(String bucketName,
                      String objectKey,
                      String uploadId,
                      int partNumber,
                      byte[] data);

    List<UploadedPart> listUploadedParts(String bucketName, String objectKey, String uploadId);

    void completeMultipartUpload(String bucketName, String objectKey, String uploadId, List<UploadedPart> uploadedParts);

    void abortMultipartUpload(String bucketName, String objectKey, String uploadId);

    StoredObjectMetadata getObjectMetadata(String bucketName, String objectKey);

    void copyObject(String sourceBucketName, String sourceObjectKey, String targetBucketName, String targetObjectKey);

    void deleteObject(String bucketName, String objectKey);
}
