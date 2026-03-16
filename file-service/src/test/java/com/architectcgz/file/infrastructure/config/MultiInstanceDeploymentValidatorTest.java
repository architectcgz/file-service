package com.architectcgz.file.infrastructure.config;

import com.platform.fileservice.core.web.config.FileCoreAccessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiInstanceDeploymentValidatorTest {

    @Test
    void shouldRejectLocalStorageWhenMultiInstanceEnabled() {
        MultiInstanceDeploymentValidator validator = newValidator(
                "local",
                null,
                null,
                "https://files.example.com",
                "secret-value",
                "jdbc:postgresql://db.example.com:5432/file_service",
                "redis.example.com",
                null
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(ex.getMessage().contains("storage.type=local"));
    }

    @Test
    void shouldRejectLoopbackGatewayBaseUrl() {
        MultiInstanceDeploymentValidator validator = newValidator(
                "s3",
                "https://cdn.example.com",
                null,
                "http://localhost:8090",
                "secret-value",
                "jdbc:postgresql://db.example.com:5432/file_service",
                "redis.example.com",
                null
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(ex.getMessage().contains("file.core.access.gateway-base-url"));
    }

    @Test
    void shouldRejectMissingPublicS3AccessAddress() {
        MultiInstanceDeploymentValidator validator = newValidator(
                "s3",
                null,
                null,
                "https://files.example.com",
                "secret-value",
                "jdbc:postgresql://db.example.com:5432/file_service",
                "redis.example.com",
                null
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(ex.getMessage().contains("storage.s3.public-endpoint or storage.s3.cdn-domain"));
    }

    @Test
    void shouldRejectDefaultSigningSecret() {
        MultiInstanceDeploymentValidator validator = newValidator(
                "s3",
                "https://s3-public.example.com",
                null,
                "https://files.example.com",
                "change-me-before-production",
                "jdbc:postgresql://db.example.com:5432/file_service",
                "redis.example.com",
                null
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(ex.getMessage().contains("signing-secret"));
    }

    @Test
    void shouldPassForSharedStorageAndExternalEndpoints() {
        MultiInstanceDeploymentValidator validator = newValidator(
                "s3",
                "https://s3-public.example.com",
                null,
                "https://files.example.com",
                "secret-value",
                "jdbc:postgresql://db.example.com:5432/file_service",
                "redis.example.com",
                null
        );

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void shouldRejectLoopbackDatasourceUrl() {
        MultiInstanceDeploymentValidator validator = newValidator(
                "s3",
                "https://s3-public.example.com",
                null,
                "https://files.example.com",
                "secret-value",
                "jdbc:postgresql://localhost:5432/file_service",
                "redis.example.com",
                null
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(ex.getMessage().contains("spring.datasource.url"));
    }

    @Test
    void shouldRejectLoopbackRedisHost() {
        MultiInstanceDeploymentValidator validator = newValidator(
                "s3",
                "https://s3-public.example.com",
                null,
                "https://files.example.com",
                "secret-value",
                "jdbc:postgresql://db.example.com:5432/file_service",
                "localhost",
                null
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(ex.getMessage().contains("spring.data.redis.host"));
    }

    @Test
    void shouldRejectLoopbackRedisUrl() {
        MultiInstanceDeploymentValidator validator = newValidator(
                "s3",
                "https://s3-public.example.com",
                null,
                "https://files.example.com",
                "secret-value",
                "jdbc:postgresql://db.example.com:5432/file_service",
                null,
                "redis://127.0.0.1:6379"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(ex.getMessage().contains("spring.data.redis.url"));
    }

    private MultiInstanceDeploymentValidator newValidator(String storageType,
                                                          String publicEndpoint,
                                                          String cdnDomain,
                                                          String gatewayBaseUrl,
                                                          String signingSecret,
                                                          String datasourceUrl,
                                                          String redisHost,
                                                          String redisUrl) {
        MultiInstanceDeploymentProperties deploymentProperties = new MultiInstanceDeploymentProperties();
        deploymentProperties.setMultiInstanceEnabled(true);

        S3Properties s3Properties = new S3Properties();
        s3Properties.setPublicEndpoint(publicEndpoint);
        s3Properties.setCdnDomain(cdnDomain);

        FileCoreAccessProperties fileCoreAccessProperties = new FileCoreAccessProperties();
        fileCoreAccessProperties.setGatewayBaseUrl(gatewayBaseUrl);
        fileCoreAccessProperties.setSigningSecret(signingSecret);

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("storage.type", storageType);
        if (datasourceUrl != null) {
            environment.setProperty("spring.datasource.url", datasourceUrl);
        }
        if (redisHost != null) {
            environment.setProperty("spring.data.redis.host", redisHost);
        }
        if (redisUrl != null) {
            environment.setProperty("spring.data.redis.url", redisUrl);
        }

        return new MultiInstanceDeploymentValidator(
                deploymentProperties,
                s3Properties,
                fileCoreAccessProperties,
                environment
        );
    }
}
