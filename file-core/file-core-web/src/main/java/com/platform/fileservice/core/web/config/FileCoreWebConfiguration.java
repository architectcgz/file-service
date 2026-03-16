package com.platform.fileservice.core.web.config;

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
import com.platform.fileservice.core.web.security.HmacAccessTicketPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V1 web facade wiring backed by direct DB and S3 adapters.
 */
@Configuration
@EnableConfigurationProperties({
        FileCoreAccessProperties.class,
        FileCoreUploadProperties.class,
        FileCoreStorageProperties.class
})
public class FileCoreWebConfiguration {

    @Bean
    AuthorizedFileAccessPort authorizedFileAccessPort(JdbcTemplate jdbcTemplate) {
        return new JdbcAuthorizedFileAccessAdapter(jdbcTemplate);
    }

    @Bean
    FileAccessMutationPort fileAccessMutationPort(JdbcTemplate jdbcTemplate) {
        return new JdbcFileAccessMutationAdapter(jdbcTemplate);
    }

    @Bean
    UploadSessionRepository uploadSessionRepository(JdbcTemplate jdbcTemplate) {
        return new PostgresUploadSessionRepository(jdbcTemplate);
    }

    @Bean
    BlobObjectRepository blobObjectRepository(JdbcTemplate jdbcTemplate) {
        return new PostgresBlobObjectRepository(jdbcTemplate);
    }

    @Bean
    FileAssetRepository fileAssetRepository(JdbcTemplate jdbcTemplate) {
        return new PostgresFileAssetRepository(jdbcTemplate);
    }

    @Bean
    ObjectStoragePort objectStoragePort(FileCoreStorageProperties properties) {
        FileCoreStorageProperties.S3 s3 = properties.getS3();
        return new S3ObjectStorageAdapter(
                s3.getEndpoint(),
                s3.getPublicEndpoint(),
                s3.getAccessKey(),
                s3.getSecretKey(),
                s3.getRegion(),
                s3.isPathStyleAccess(),
                s3.getBucket(),
                s3.getPublicBucket(),
                s3.getPrivateBucket(),
                s3.getCdnDomain()
        );
    }

    @Bean
    AccessTicketPort accessTicketPort(FileCoreAccessProperties properties) {
        return new HmacAccessTicketPort(properties.getSigningSecret());
    }

    @Bean
    ClockPort clockPort() {
        return java.time.Instant::now;
    }

    @Bean
    TransactionOperations transactionOperations(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    AccessAppService accessAppService(AuthorizedFileAccessPort authorizedFileAccessPort,
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
    UploadAppService uploadAppService(UploadSessionRepository uploadSessionRepository,
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
    CleanupAppService cleanupAppService(UploadSessionRepository uploadSessionRepository,
                                        ObjectStoragePort objectStoragePort,
                                        ClockPort clockPort) {
        return new CleanupAppService(uploadSessionRepository, objectStoragePort, clockPort);
    }
}
