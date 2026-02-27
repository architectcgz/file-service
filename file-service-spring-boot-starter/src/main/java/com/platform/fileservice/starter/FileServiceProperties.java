package com.platform.fileservice.starter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件服务客户端的Spring Boot配置属性
 * 
 * 此类映射application.yml或application.properties中的配置属性，
 * 前缀为"file-service.client"。
 * 
 * 示例配置：
 * <pre>
 * file-service:
 *   client:
 *     server-url: http://localhost:8089
 *     tenant-id: blog
 *     token: ${JWT_TOKEN}
 *     custom-domain: https://files.example.com
 *     cdn-domain: https://cdn.example.com
 *     connect-timeout: 10000
 *     read-timeout: 30000
 *     max-connections: 50
 * </pre>
 *
 * @author File Service Team
 */
@Data
@ConfigurationProperties(prefix = "file-service.client")
public class FileServiceProperties {
    
    /**
     * 文件服务服务器URL（必需）
     * 例如：http://localhost:8089
     */
    private String serverUrl;
    
    /**
     * 租户ID/应用程序ID（必需）
     * 用于多租户隔离，将作为X-App-Id请求头发送
     */
    private String tenantId;
    
    /**
     * 静态认证令牌（可选）
     * 如果未提供，将尝试从Spring Security上下文获取令牌
     * 令牌应不带"Bearer "前缀
     */
    private String token;
    
    /**
     * 连接超时时间（毫秒）
     * 默认：10000（10秒）
     */
    private int connectTimeout = 10000;
    
    /**
     * 读取超时时间（毫秒）
     * 默认：30000（30秒）
     */
    private int readTimeout = 30000;
    
    /**
     * HTTP连接池中的最大连接数
     * 默认：50
     */
    private int maxConnections = 50;
    
    /**
     * 文件URL的自定义域名（可选）
     * 设置后，服务器返回的文件URL将其域名替换为此自定义域名
     * 例如：https://files.example.com
     */
    private String customDomain;
    
    /**
     * 公开文件URL的CDN域名（可选）
     * 设置后，公开文件URL将使用此CDN域名
     * 例如：https://cdn.example.com
     */
    private String cdnDomain;
    
    /**
     * 失败请求的最大重试次数
     * 默认：3
     */
    private int maxRetries = 3;
    
    /**
     * 重试尝试之间的延迟时间（毫秒）
     * 默认：1000（1秒）
     */
    private long retryDelayMs = 1000;
}
