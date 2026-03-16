package com.architectcgz.file.infrastructure.config;

import com.platform.fileservice.core.adapters.db.JdbcAuthorizedFileAccessAdapter;
import com.platform.fileservice.core.adapters.db.JdbcFileAccessMutationAdapter;
import com.platform.fileservice.core.adapters.db.PostgresBlobObjectRepository;
import com.platform.fileservice.core.adapters.db.PostgresFileAssetRepository;
import com.platform.fileservice.core.adapters.db.PostgresUploadSessionRepository;
import com.platform.fileservice.core.adapters.s3.S3ObjectStorageAdapter;
import com.platform.fileservice.core.application.service.AccessAppService;
import com.platform.fileservice.core.application.service.CleanupAppService;
import com.platform.fileservice.core.application.service.UploadAppService;
import com.platform.fileservice.core.ports.access.AuthorizedFileAccessPort;
import com.platform.fileservice.core.ports.access.FileAccessMutationPort;
import com.platform.fileservice.core.ports.repository.BlobObjectRepository;
import com.platform.fileservice.core.ports.repository.FileAssetRepository;
import com.platform.fileservice.core.ports.repository.UploadSessionRepository;
import com.platform.fileservice.core.ports.security.AccessTicketPort;
import com.platform.fileservice.core.ports.storage.ObjectStoragePort;
import com.platform.fileservice.core.ports.system.ClockPort;
import com.platform.fileservice.core.web.config.FileCoreAccessProperties;
import com.platform.fileservice.core.web.config.FileCoreUploadProperties;
import com.platform.fileservice.core.web.controller.V1FileController;
import com.platform.fileservice.core.web.exception.V1GlobalExceptionHandler;
import com.platform.fileservice.core.web.security.HmacAccessTicketPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;

/**
 * Legacy file-service bridge wiring for file-core application services.
 */
@Configuration
@EnableConfigurationProperties({
        FileCoreAccessProperties.class,
        FileCoreUploadProperties.class
})
public class CoreBridgeConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuthorizedFileAccessPort.class)
    AuthorizedFileAccessPort coreAuthorizedFileAccessPort(JdbcTemplate jdbcTemplate) {
        return new JdbcAuthorizedFileAccessAdapter(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(FileAccessMutationPort.class)
    FileAccessMutationPort coreFileAccessMutationPort(JdbcTemplate jdbcTemplate) {
        return new JdbcFileAccessMutationAdapter(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(UploadSessionRepository.class)
    UploadSessionRepository coreUploadSessionRepository(JdbcTemplate jdbcTemplate) {
        return new PostgresUploadSessionRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(BlobObjectRepository.class)
    BlobObjectRepository coreBlobObjectRepository(JdbcTemplate jdbcTemplate) {
        return new PostgresBlobObjectRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(FileAssetRepository.class)
    FileAssetRepository coreFileAssetRepository(JdbcTemplate jdbcTemplate) {
        return new PostgresFileAssetRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(ObjectStoragePort.class)
    ObjectStoragePort coreObjectStoragePort(S3Properties properties) {
        return new S3ObjectStorageAdapter(
                URI.create(properties.getEndpoint()),
                properties.getPublicEndpoint() == null || properties.getPublicEndpoint().isBlank()
                        ? null
                        : URI.create(properties.getPublicEndpoint()),
                properties.getAccessKey(),
                properties.getSecretKey(),
                properties.getRegion(),
                properties.isPathStyleAccess(),
                properties.getBucket(),
                properties.getPublicBucket(),
                properties.getPrivateBucket(),
                properties.getCdnDomain()
        );
    }

    @Bean
    @ConditionalOnMissingBean(ClockPort.class)
    ClockPort coreClockPort() {
        return java.time.Instant::now;
    }

    @Bean
    @ConditionalOnMissingBean(TransactionOperations.class)
    TransactionOperations coreTransactionOperations(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean(AccessTicketPort.class)
    AccessTicketPort coreAccessTicketPort(FileCoreAccessProperties properties) {
        return new HmacAccessTicketPort(properties.getSigningSecret());
    }

    @Bean
    @ConditionalOnMissingBean(AccessAppService.class)
    AccessAppService coreAccessAppService(AuthorizedFileAccessPort authorizedFileAccessPort,
                                          FileAccessMutationPort fileAccessMutationPort,
                                          ObjectStoragePort objectStoragePort,
                                          AccessTicketPort accessTicketPort,
                                          ClockPort clockPort,
                                          TransactionOperations transactionOperations) {
        return new AccessAppService(
                authorizedFileAccessPort,
                fileAccessMutationPort,
                objectStoragePort,
                accessTicketPort,
                clockPort,
                transactionOperations
        );
    }

    @Bean
    @ConditionalOnMissingBean(UploadAppService.class)
    UploadAppService coreUploadAppService(UploadSessionRepository uploadSessionRepository,
                                          BlobObjectRepository blobObjectRepository,
                                          FileAssetRepository fileAssetRepository,
                                          ObjectStoragePort objectStoragePort,
                                          ClockPort clockPort,
                                          TransactionOperations transactionOperations,
                                          FileCoreUploadProperties uploadProperties) {
        return new UploadAppService(
                uploadSessionRepository,
                blobObjectRepository,
                fileAssetRepository,
                objectStoragePort,
                clockPort,
                transactionOperations,
                uploadProperties.getCompletionWaitTimeout(),
                uploadProperties.getCompletionPollInterval()
        );
    }

    @Bean
    @ConditionalOnMissingBean(CleanupAppService.class)
    CleanupAppService coreCleanupAppService(UploadSessionRepository uploadSessionRepository,
                                            ObjectStoragePort objectStoragePort,
                                            ClockPort clockPort) {
        return new CleanupAppService(uploadSessionRepository, objectStoragePort, clockPort);
    }

    @Bean
    @ConditionalOnMissingBean(V1FileController.class)
    V1FileController coreV1FileController(AccessAppService accessAppService,
                                          FileCoreAccessProperties fileCoreAccessProperties) {
        return new V1FileController(accessAppService, fileCoreAccessProperties);
    }

    @Bean
    @ConditionalOnMissingBean(V1GlobalExceptionHandler.class)
    V1GlobalExceptionHandler coreV1GlobalExceptionHandler() {
        return new V1GlobalExceptionHandler();
    }
}
