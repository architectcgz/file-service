package com.platform.fileservice.core.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * Runtime storage settings consumed by the V1 access facade.
 */
@ConfigurationProperties(prefix = "file.core.storage")
public class FileCoreStorageProperties {

    private final S3 s3 = new S3();

    public S3 getS3() {
        return s3;
    }

    public static class S3 {

        private URI endpoint;
        private URI publicEndpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        private String publicBucket;
        private String privateBucket;
        private String region = "us-east-1";
        private String cdnDomain;
        private boolean pathStyleAccess = true;

        public URI getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(URI endpoint) {
            this.endpoint = endpoint;
        }

        public URI getPublicEndpoint() {
            return publicEndpoint;
        }

        public void setPublicEndpoint(URI publicEndpoint) {
            this.publicEndpoint = publicEndpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getPublicBucket() {
            return publicBucket;
        }

        public void setPublicBucket(String publicBucket) {
            this.publicBucket = publicBucket;
        }

        public String getPrivateBucket() {
            return privateBucket;
        }

        public void setPrivateBucket(String privateBucket) {
            this.privateBucket = privateBucket;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getCdnDomain() {
            return cdnDomain;
        }

        public void setCdnDomain(String cdnDomain) {
            this.cdnDomain = cdnDomain;
        }

        public boolean isPathStyleAccess() {
            return pathStyleAccess;
        }

        public void setPathStyleAccess(boolean pathStyleAccess) {
            this.pathStyleAccess = pathStyleAccess;
        }
    }
}
