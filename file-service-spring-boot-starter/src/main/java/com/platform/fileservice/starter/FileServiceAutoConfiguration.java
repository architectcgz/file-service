package com.platform.fileservice.starter;

import com.platform.fileservice.client.FileServiceClient;
import com.platform.fileservice.client.FileServiceClientImpl;
import com.platform.fileservice.client.config.FileServiceClientConfig;
import com.platform.fileservice.client.config.TokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 文件服务客户端的Spring Boot自动配置
 * 
 * 此配置类自动设置FileServiceClient bean，使其可用于依赖注入。
 * 它从application.yml/properties读取配置，并创建必要的bean。
 * 
 * 自动配置的bean：
 * - TokenProvider：从Spring Security上下文或静态配置提供令牌
 * - FileServiceClient：主要的文件服务客户端
 * 
 * 使用@ConditionalOnMissingBean允许用户通过定义自己的bean来覆盖默认配置。
 * 
 * 示例使用：
 * <pre>
 * {@code @Service}
 * public class MyService {
 *     {@code @Autowired}
 *     private FileServiceClient fileServiceClient;
 *     
 *     public void uploadFile(File file) {
 *         FileUploadResponse response = fileServiceClient.uploadFile(file);
 *         // ...
 *     }
 * }
 * </pre>
 *
 * @author File Service Team
 */
@AutoConfiguration
@ConditionalOnClass(FileServiceClient.class)
@EnableConfigurationProperties(FileServiceProperties.class)
public class FileServiceAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(FileServiceAutoConfiguration.class);
    
    /**
     * 创建TokenProvider bean
     * 
     * 此bean提供认证令牌。它首先尝试从Spring Security上下文获取令牌，
     * 如果不可用则回退到配置的静态令牌。
     * 
     * 使用@ConditionalOnMissingBean允许用户提供自定义TokenProvider实现。
     *
     * @param properties 文件服务配置属性
     * @return TokenProvider实例
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenProvider tokenProvider(FileServiceProperties properties) {
        logger.info("创建SpringTokenProvider bean");
        return new SpringTokenProvider(properties.getToken());
    }
    
    /**
     * 创建FileServiceClient bean
     * 
     * 此bean是与文件服务交互的主要客户端。
     * 它使用配置属性和TokenProvider进行初始化。
     * 
     * 使用@ConditionalOnMissingBean允许用户提供自定义FileServiceClient实现。
     *
     * @param properties 文件服务配置属性
     * @param tokenProvider 令牌提供者
     * @return FileServiceClient实例
     * @throws IllegalArgumentException 如果配置无效
     */
    @Bean
    @ConditionalOnMissingBean
    public FileServiceClient fileServiceClient(
            FileServiceProperties properties,
            TokenProvider tokenProvider) {
        
        logger.info("创建FileServiceClient bean");
        logger.debug("服务器URL: {}", properties.getServerUrl());
        logger.debug("租户ID: {}", properties.getTenantId());
        
        // 从属性构建配置
        FileServiceClientConfig config = FileServiceClientConfig.builder()
                .serverUrl(properties.getServerUrl())
                .tenantId(properties.getTenantId())
                .tokenProvider(tokenProvider)
                .connectTimeout(properties.getConnectTimeout())
                .readTimeout(properties.getReadTimeout())
                .maxConnections(properties.getMaxConnections())
                .customDomain(properties.getCustomDomain())
                .cdnDomain(properties.getCdnDomain())
                .maxRetries(properties.getMaxRetries())
                .retryDelayMs(properties.getRetryDelayMs())
                .build();
        
        // 验证配置
        try {
            config.validate();
        } catch (IllegalArgumentException e) {
            logger.error("文件服务客户端配置无效: {}", e.getMessage());
            throw e;
        }
        
        logger.info("文件服务客户端已成功配置");
        return new FileServiceClientImpl(config);
    }
}
