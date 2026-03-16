package com.platform.fileservice.core.adapters.s3;

import com.platform.fileservice.core.domain.exception.FileAccessMutationException;
import com.platform.fileservice.core.domain.exception.UploadSessionMutationException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.StoredObjectMetadata;
import com.platform.fileservice.core.domain.model.UploadedPart;
import com.platform.fileservice.core.ports.storage.ObjectStoragePort;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Minimal S3 adapter used by the access-policy migration path.
 */
public final class S3ObjectStorageAdapter implements ObjectStoragePort {

    private final URI baseEndpoint;
    private final URI publicEndpoint;
    private final String defaultBucketName;
    private final String publicBucketName;
    private final String privateBucketName;
    private final String cdnDomain;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3ObjectStorageAdapter(URI baseEndpoint,
                                  URI publicEndpoint,
                                  String accessKey,
                                  String secretKey,
                                  String region,
                                  boolean pathStyleAccess,
                                  String defaultBucketName,
                                  String publicBucketName,
                                  String privateBucketName,
                                  String cdnDomain) {
        this.baseEndpoint = Objects.requireNonNull(baseEndpoint, "baseEndpoint must not be null");
        this.publicEndpoint = publicEndpoint != null ? publicEndpoint : baseEndpoint;
        this.defaultBucketName = defaultBucketName;
        this.publicBucketName = publicBucketName;
        this.privateBucketName = privateBucketName;
        this.cdnDomain = cdnDomain;
        this.s3Client = buildClient(baseEndpoint, accessKey, secretKey, region, pathStyleAccess);
        this.s3Presigner = buildPresigner(baseEndpoint, accessKey, secretKey, region, pathStyleAccess);
    }

    @Override
    public URI resolveObjectUri(String bucketName, String objectKey) {
        if (cdnDomain != null && !cdnDomain.isBlank()) {
            String normalizedDomain = cdnDomain.endsWith("/") ? cdnDomain : cdnDomain + "/";
            return URI.create(normalizedDomain + objectKey);
        }
        String normalizedPath = String.format("%s/%s", normalizeBucketName(bucketName), objectKey);
        return publicEndpoint.resolve(publicEndpoint.getPath().endsWith("/")
                ? normalizedPath
                : "/" + normalizedPath);
    }

    @Override
    public String resolveBucketName(AccessLevel accessLevel) {
        return normalizeBucketName(accessLevel == AccessLevel.PUBLIC ? publicBucketName : privateBucketName);
    }

    @Override
    public String normalizeBucketName(String bucketName) {
        if (bucketName != null && !bucketName.isBlank()) {
            return bucketName;
        }
        return defaultBucketName;
    }

    @Override
    public String createMultipartUpload(String bucketName, String objectKey, String contentType) {
        try {
            return s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                            .bucket(normalizeBucketName(bucketName))
                            .key(objectKey)
                            .contentType(contentType)
                            .build())
                    .uploadId();
        } catch (S3Exception | SdkClientException ex) {
            throw new UploadSessionMutationException("failed to create multipart upload", ex);
        }
    }

    @Override
    public String generatePresignedPutObjectUrl(String bucketName,
                                                String objectKey,
                                                String contentType,
                                                Duration ttl) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(normalizeBucketName(bucketName))
                    .key(objectKey)
                    .contentType(contentType)
                    .build();
            return s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                            .signatureDuration(ttl)
                            .putObjectRequest(putObjectRequest)
                            .build())
                    .url()
                    .toString();
        } catch (S3Exception | SdkClientException ex) {
            throw new UploadSessionMutationException("failed to generate presigned put object url", ex);
        }
    }

    @Override
    public String generatePresignedUploadPartUrl(String bucketName,
                                                 String objectKey,
                                                 String uploadId,
                                                 int partNumber,
                                                 Duration ttl) {
        try {
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(normalizeBucketName(bucketName))
                    .key(objectKey)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();
            return s3Presigner.presignUploadPart(UploadPartPresignRequest.builder()
                            .signatureDuration(ttl)
                            .uploadPartRequest(uploadPartRequest)
                            .build())
                    .url()
                    .toString();
        } catch (S3Exception | SdkClientException ex) {
            throw new UploadSessionMutationException("failed to generate presigned upload part url", ex);
        }
    }

    @Override
    public String uploadPart(String bucketName,
                             String objectKey,
                             String uploadId,
                             int partNumber,
                             byte[] data) {
        try {
            return s3Client.uploadPart(UploadPartRequest.builder()
                            .bucket(normalizeBucketName(bucketName))
                            .key(objectKey)
                            .uploadId(uploadId)
                            .partNumber(partNumber)
                            .contentLength((long) data.length)
                            .build(), RequestBody.fromBytes(data))
                    .eTag();
        } catch (S3Exception | SdkClientException ex) {
            throw new UploadSessionMutationException("failed to upload multipart part", ex);
        }
    }

    @Override
    public List<UploadedPart> listUploadedParts(String bucketName, String objectKey, String uploadId) {
        try {
            List<UploadedPart> uploadedParts = new ArrayList<>();
            String normalizedBucketName = normalizeBucketName(bucketName);
            Integer marker = null;
            boolean truncated = true;
            while (truncated) {
                var response = s3Client.listParts(ListPartsRequest.builder()
                        .bucket(normalizedBucketName)
                        .key(objectKey)
                        .uploadId(uploadId)
                        .partNumberMarker(marker)
                        .build());
                for (Part part : response.parts()) {
                    uploadedParts.add(new UploadedPart(
                            part.partNumber(),
                            part.eTag(),
                            part.size()
                    ));
                }
                truncated = response.isTruncated();
                marker = response.nextPartNumberMarker();
            }
            uploadedParts.sort(Comparator.comparingInt(UploadedPart::partNumber));
            return uploadedParts;
        } catch (S3Exception | SdkClientException ex) {
            throw new UploadSessionMutationException("failed to list multipart upload parts", ex);
        }
    }

    @Override
    public void completeMultipartUpload(String bucketName,
                                        String objectKey,
                                        String uploadId,
                                        List<UploadedPart> uploadedParts) {
        try {
            List<CompletedPart> completedParts = uploadedParts.stream()
                    .sorted(Comparator.comparingInt(UploadedPart::partNumber))
                    .map(part -> CompletedPart.builder()
                            .partNumber(part.partNumber())
                            .eTag(part.etag())
                            .build())
                    .toList();
            s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(normalizeBucketName(bucketName))
                    .key(objectKey)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder()
                            .parts(completedParts)
                            .build())
                    .build());
        } catch (S3Exception | SdkClientException ex) {
            throw new UploadSessionMutationException("failed to complete multipart upload", ex);
        }
    }

    @Override
    public void abortMultipartUpload(String bucketName, String objectKey, String uploadId) {
        try {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(normalizeBucketName(bucketName))
                    .key(objectKey)
                    .uploadId(uploadId)
                    .build());
        } catch (S3Exception | SdkClientException ex) {
            throw new UploadSessionMutationException("failed to abort multipart upload", ex);
        }
    }

    @Override
    public StoredObjectMetadata getObjectMetadata(String bucketName, String objectKey) {
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(normalizeBucketName(bucketName))
                    .key(objectKey)
                    .build());
            return new StoredObjectMetadata(
                    response.contentLength(),
                    response.contentType()
            );
        } catch (S3Exception | SdkClientException ex) {
            throw new UploadSessionMutationException("failed to read object metadata", ex);
        }
    }

    @Override
    public void copyObject(String sourceBucketName, String sourceObjectKey, String targetBucketName, String targetObjectKey) {
        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .copySource(normalizeBucketName(sourceBucketName) + "/" + sourceObjectKey)
                    .destinationBucket(normalizeBucketName(targetBucketName))
                    .destinationKey(targetObjectKey)
                    .build());
        } catch (S3Exception | SdkClientException ex) {
            throw new FileAccessMutationException("failed to copy object in storage", ex);
        }
    }

    @Override
    public void deleteObject(String bucketName, String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(normalizeBucketName(bucketName))
                    .key(objectKey)
                    .build());
        } catch (S3Exception | SdkClientException ex) {
            throw new FileAccessMutationException("failed to delete object in storage", ex);
        }
    }

    private S3Client buildClient(URI endpoint,
                                 String accessKey,
                                 String secretKey,
                                 String region,
                                 boolean pathStyleAccess) {
        try {
            return S3Client.builder()
                    .endpointOverride(endpoint)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)
                    ))
                    .region(Region.of(region))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(pathStyleAccess)
                            .build())
                    .build();
        } catch (RuntimeException ex) {
            throw new FileAccessMutationException("failed to initialize s3 client", ex);
        }
    }

    private S3Presigner buildPresigner(URI endpoint,
                                       String accessKey,
                                       String secretKey,
                                       String region,
                                       boolean pathStyleAccess) {
        try {
            return S3Presigner.builder()
                    .endpointOverride(endpoint)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)
                    ))
                    .region(Region.of(region))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(pathStyleAccess)
                            .build())
                    .build();
        } catch (RuntimeException ex) {
            throw new UploadSessionMutationException("failed to initialize s3 presigner", ex);
        }
    }
}
