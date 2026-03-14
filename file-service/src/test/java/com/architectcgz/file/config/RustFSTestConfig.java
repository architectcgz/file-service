package com.architectcgz.file.config;

import com.architectcgz.file.infrastructure.config.S3Properties;
import com.architectcgz.file.integration.helper.S3Verifier;
import com.architectcgz.file.integration.helper.URLAccessVerifier;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Test configuration for RustFS integration tests
 * Provides real S3Client and HttpClient beans for testing
 * Does NOT mock S3StorageService - uses real implementation
 */
@TestConfiguration
@Profile("rustfs-test")
public class RustFSTestConfig {
    
    /**
     * Create S3Client for direct RustFS verification
     * This client is used by test helper classes to verify file storage
     * 
     * @param properties S3 configuration properties
     * @return configured S3Client
     */
    @Bean
    public S3Client testS3Client(S3Properties properties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKey(),
                properties.getSecretKey()
        );
        
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.isPathStyleAccess())
                .build();
        
        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Config)
                .build();
    }

    @Bean
    public S3Verifier s3Verifier(S3Client testS3Client, @Value("${storage.s3.bucket}") String bucket) {
        return new S3Verifier(testS3Client, bucket);
    }

    @Bean
    public URLAccessVerifier urlAccessVerifier() {
        return new URLAccessVerifier();
    }
    
    /**
     * Create HttpClient for URL access testing
     * This client is used to verify files can be accessed via returned URLs
     * 
     * @return configured CloseableHttpClient
     */
    @Bean
    public CloseableHttpClient httpClient() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(50);
        connectionManager.setDefaultMaxPerRoute(10);
        
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(true)
                .setDefaultRequestConfig(
                        org.apache.hc.client5.http.config.RequestConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(10))
                                .setResponseTimeout(Timeout.ofSeconds(30))
                                .build()
                )
                .build();
    }
}
