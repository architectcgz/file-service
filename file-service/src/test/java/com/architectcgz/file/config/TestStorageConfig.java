package com.architectcgz.file.config;

import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.infrastructure.image.ImageProcessor;
import com.architectcgz.file.infrastructure.storage.LocalStorageService;
import com.architectcgz.file.infrastructure.storage.StorageService;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration for storage services
 * Provides mock S3StorageService when using local storage in tests
 */
@TestConfiguration
@Profile("test")
public class TestStorageConfig {

    /**
     * Force StorageService injections to use the real LocalStorageService during integration tests.
     * The mocked S3StorageService still exists for beans that depend on the concrete S3 type.
     */
    @Bean
    @Primary
    public StorageService storageService(LocalStorageService localStorageService) {
        return localStorageService;
    }
    
    /**
     * Mock S3StorageService for tests using local storage
     * This prevents NoSuchBeanDefinitionException when FileAccessService
     * tries to inject S3StorageService
     * 
     * Note: This is NOT a StorageService implementation, so it won't conflict
     * with LocalStorageService for StorageService injection
     */
    @Bean
    public S3StorageService s3StorageService() {
        S3StorageService mock = Mockito.mock(S3StorageService.class);
        // Configure default mock behavior if needed
        Mockito.when(mock.getBucketName(Mockito.any()))
                .thenReturn("local");
        Mockito.when(mock.getUrl(Mockito.anyString()))
                .thenAnswer(invocation -> "http://localhost:8089/files/" + invocation.getArgument(0));
        Mockito.when(mock.generatePresignedGetUrl(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(invocation -> "http://localhost:8089/files/" + invocation.getArgument(0) + "?presigned=true");
        return mock;
    }
    
    /**
     * Mock FileTypeValidator to bypass file type validation in integration tests
     * This allows tests to use simple text content instead of real file magic numbers
     */
    @Bean
    @Primary
    public FileTypeValidator fileTypeValidator() {
        FileTypeValidator mock = Mockito.mock(FileTypeValidator.class);
        // Configure to do nothing (bypass validation)
        Mockito.doNothing().when(mock).validateFileWithMagicNumber(
                Mockito.anyString(), 
                Mockito.anyString(), 
                Mockito.any(byte[].class), 
                Mockito.anyLong());
        return mock;
    }
    
    /**
     * Mock ImageProcessor to bypass image processing in integration tests
     * This allows tests to use simple text content instead of real image data
     */
    @Bean
    @Primary
    public ImageProcessor imageProcessor() {
        ImageProcessor mock = Mockito.mock(ImageProcessor.class);
        // Configure to return input data as-is (bypass processing)
        Mockito.when(mock.process(Mockito.any(byte[].class), Mockito.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(mock.generateThumbnail(Mockito.any(byte[].class), Mockito.anyInt(), Mockito.anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return mock;
    }
}
