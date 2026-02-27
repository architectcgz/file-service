package com.platform.fileservice.starter;

import com.platform.fileservice.client.FileServiceClient;
import com.platform.fileservice.client.config.TokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件服务自动配置测试
 * 
 * 测试Spring Boot自动配置是否正确创建必要的bean。
 *
 * @author File Service Team
 */
class FileServiceAutoConfigurationTest {
    
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FileServiceAutoConfiguration.class));
    
    /**
     * 测试：使用有效配置时应创建FileServiceClient bean
     */
    @Test
    void shouldCreateFileServiceClientBeanWithValidConfiguration() {
        this.contextRunner
                .withPropertyValues(
                        "file-service.client.server-url=http://localhost:8089",
                        "file-service.client.tenant-id=test-app",
                        "file-service.client.token=test-token"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FileServiceClient.class);
                    assertThat(context).hasSingleBean(TokenProvider.class);
                    assertThat(context).hasSingleBean(FileServiceProperties.class);
                });
    }
    
    /**
     * 测试：缺少必需配置时上下文启动应失败
     */
    @Test
    void shouldFailToStartWithoutRequiredConfiguration() {
        this.contextRunner
                .run(context -> {
                    // 没有配置时，上下文启动应失败
                    assertThat(context).hasFailed();
                });
    }
    
    /**
     * 测试：应使用配置的默认值
     */
    @Test
    void shouldUseDefaultValues() {
        this.contextRunner
                .withPropertyValues(
                        "file-service.client.server-url=http://localhost:8089",
                        "file-service.client.tenant-id=test-app",
                        "file-service.client.token=test-token"
                )
                .run(context -> {
                    FileServiceProperties properties = context.getBean(FileServiceProperties.class);
                    assertThat(properties.getConnectTimeout()).isEqualTo(10000);
                    assertThat(properties.getReadTimeout()).isEqualTo(30000);
                    assertThat(properties.getMaxConnections()).isEqualTo(50);
                    assertThat(properties.getMaxRetries()).isEqualTo(3);
                    assertThat(properties.getRetryDelayMs()).isEqualTo(1000);
                });
    }
    
    /**
     * 测试：应允许覆盖默认值
     */
    @Test
    void shouldAllowOverridingDefaultValues() {
        this.contextRunner
                .withPropertyValues(
                        "file-service.client.server-url=http://localhost:8089",
                        "file-service.client.tenant-id=test-app",
                        "file-service.client.token=test-token",
                        "file-service.client.connect-timeout=5000",
                        "file-service.client.read-timeout=15000",
                        "file-service.client.max-connections=100"
                )
                .run(context -> {
                    FileServiceProperties properties = context.getBean(FileServiceProperties.class);
                    assertThat(properties.getConnectTimeout()).isEqualTo(5000);
                    assertThat(properties.getReadTimeout()).isEqualTo(15000);
                    assertThat(properties.getMaxConnections()).isEqualTo(100);
                });
    }
    
    /**
     * 测试：应支持自定义域名配置
     */
    @Test
    void shouldSupportCustomDomainConfiguration() {
        this.contextRunner
                .withPropertyValues(
                        "file-service.client.server-url=http://localhost:8089",
                        "file-service.client.tenant-id=test-app",
                        "file-service.client.token=test-token",
                        "file-service.client.custom-domain=https://files.example.com",
                        "file-service.client.cdn-domain=https://cdn.example.com"
                )
                .run(context -> {
                    FileServiceProperties properties = context.getBean(FileServiceProperties.class);
                    assertThat(properties.getCustomDomain()).isEqualTo("https://files.example.com");
                    assertThat(properties.getCdnDomain()).isEqualTo("https://cdn.example.com");
                });
    }
    
    /**
     * 测试：应允许用户提供自定义TokenProvider
     */
    @Test
    void shouldAllowCustomTokenProvider() {
        this.contextRunner
                .withPropertyValues(
                        "file-service.client.server-url=http://localhost:8089",
                        "file-service.client.tenant-id=test-app"
                )
                .withBean(TokenProvider.class, () -> TokenProvider.fixed("custom-token"))
                .run(context -> {
                    assertThat(context).hasSingleBean(TokenProvider.class);
                    TokenProvider tokenProvider = context.getBean(TokenProvider.class);
                    assertThat(tokenProvider.getToken()).isEqualTo("custom-token");
                });
    }
}
