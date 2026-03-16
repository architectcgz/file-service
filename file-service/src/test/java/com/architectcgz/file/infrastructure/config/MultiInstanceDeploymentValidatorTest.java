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
                "secret-value"
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
                "secret-value"
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
                "secret-value"
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
                "change-me-before-production"
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
                "secret-value"
        );

        assertDoesNotThrow(validator::validate);
    }

    private MultiInstanceDeploymentValidator newValidator(String storageType,
                                                          String publicEndpoint,
                                                          String cdnDomain,
                                                          String gatewayBaseUrl,
                                                          String signingSecret) {
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

        return new MultiInstanceDeploymentValidator(
                deploymentProperties,
                s3Properties,
                fileCoreAccessProperties,
                environment
        );
    }
}
