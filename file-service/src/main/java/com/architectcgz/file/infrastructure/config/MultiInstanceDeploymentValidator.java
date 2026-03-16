package com.architectcgz.file.infrastructure.config;

import com.platform.fileservice.core.web.config.FileCoreAccessProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * 多实例部署启动前置校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiInstanceDeploymentValidator implements InitializingBean {

    private static final String DEFAULT_STORAGE_TYPE = "s3";
    private static final String DEFAULT_SIGNING_SECRET = "change-me-before-production";
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "0.0.0.0");

    private final MultiInstanceDeploymentProperties deploymentProperties;
    private final S3Properties s3Properties;
    private final FileCoreAccessProperties fileCoreAccessProperties;
    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    void validate() {
        if (!deploymentProperties.isMultiInstanceEnabled()) {
            return;
        }

        String storageType = environment.getProperty("storage.type", DEFAULT_STORAGE_TYPE);
        requireNonLoopbackJdbcUrl("spring.datasource.url", environment.getProperty("spring.datasource.url"));
        requireNonLoopbackRedisAddress();

        if ("local".equalsIgnoreCase(storageType)) {
            fail("multi-instance deployment requires shared object storage; storage.type=local is not supported");
        }

        requireNonLoopbackUrl("file.core.access.gateway-base-url", fileCoreAccessProperties.getGatewayBaseUrl());

        if (DEFAULT_SIGNING_SECRET.equals(fileCoreAccessProperties.getSigningSecret())) {
            fail("multi-instance deployment requires a non-default file.core.access.signing-secret");
        }

        if ("s3".equalsIgnoreCase(storageType)) {
            validateS3PublicAccess();
        }

        log.info("Multi-instance deployment prerequisites validated: storageType={}", storageType);
    }

    private void validateS3PublicAccess() {
        if (StringUtils.hasText(s3Properties.getCdnDomain())) {
            requireNonLoopbackUrl("storage.s3.cdn-domain", s3Properties.getCdnDomain());
            return;
        }

        if (!StringUtils.hasText(s3Properties.getPublicEndpoint())) {
            fail("multi-instance deployment requires storage.s3.public-endpoint or storage.s3.cdn-domain");
        }

        requireNonLoopbackUrl("storage.s3.public-endpoint", s3Properties.getPublicEndpoint());
    }

    private void requireNonLoopbackJdbcUrl(String propertyName, String propertyValue) {
        if (!StringUtils.hasText(propertyValue)) {
            fail("multi-instance deployment requires " + propertyName + " to be configured");
        }

        String normalized = propertyValue.startsWith("jdbc:") ? propertyValue.substring(5) : propertyValue;
        URI uri = parseUri(propertyName, normalized);
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            fail(propertyName + " must contain a valid JDBC URL with host");
        }
        requireNonLoopbackHost(propertyName, host);
    }

    private void requireNonLoopbackRedisAddress() {
        String redisUrl = environment.getProperty("spring.data.redis.url");
        if (StringUtils.hasText(redisUrl)) {
            URI uri = parseUri("spring.data.redis.url", redisUrl);
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                fail("spring.data.redis.url must contain a valid absolute URL");
            }
            requireNonLoopbackHost("spring.data.redis.url", host);
            return;
        }

        String redisHost = environment.getProperty("spring.data.redis.host");
        if (!StringUtils.hasText(redisHost)) {
            fail("multi-instance deployment requires spring.data.redis.host or spring.data.redis.url to be configured");
        }
        requireNonLoopbackHost("spring.data.redis.host", redisHost);
    }

    private void requireNonLoopbackUrl(String propertyName, String propertyValue) {
        if (!StringUtils.hasText(propertyValue)) {
            fail("multi-instance deployment requires " + propertyName + " to be configured");
        }

        URI uri = parseUri(propertyName, propertyValue);
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            fail(propertyName + " must contain a valid absolute URL");
        }

        requireNonLoopbackHost(propertyName, host);
    }

    private void requireNonLoopbackHost(String propertyName, String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (LOOPBACK_HOSTS.contains(normalizedHost)) {
            fail("multi-instance deployment requires " + propertyName + " to be externally reachable, but found loopback host: " + host);
        }
    }

    private URI parseUri(String propertyName, String propertyValue) {
        try {
            return new URI(propertyValue);
        } catch (URISyntaxException ex) {
            fail(propertyName + " must be a valid absolute URL", ex);
            return null;
        }
    }

    private void fail(String message) {
        throw new IllegalStateException(message);
    }

    private void fail(String message, Exception cause) {
        throw new IllegalStateException(message, cause);
    }
}
