package com.platform.fileservice.client.config;

/**
 * 文件服务客户端配置类
 * 
 * 此类保存初始化和操作文件服务客户端所需的所有配置参数，
 * 包括连接设置、认证和URL替换的域名配置。
 * 
 * 使用构建器模式构造实例：
 * <pre>
 * FileServiceClientConfig config = FileServiceClientConfig.builder()
 *     .serverUrl("http://localhost:8089")
 *     .tenantId("my-app")
 *     .tokenProvider(TokenProvider.fixed("my-token"))
 *     .build();
 * </pre>
 *
 * @author File Service Team
 */
public class FileServiceClientConfig {
    
    // 必需字段
    private final String serverUrl;
    private final String tenantId;
    private final TokenProvider tokenProvider;
    
    // 可选 - 连接设置
    private final int connectTimeout;
    private final int readTimeout;
    private final int maxConnections;
    
    // 可选 - 域名配置
    private final String customDomain;
    private final String cdnDomain;
    
    // 可选 - 重试设置
    private final int maxRetries;
    private final long retryDelayMs;
    
    /**
     * 私有构造函数 - 使用builder()创建实例
     */
    private FileServiceClientConfig(Builder builder) {
        this.serverUrl = builder.serverUrl;
        this.tenantId = builder.tenantId;
        this.tokenProvider = builder.tokenProvider;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.maxConnections = builder.maxConnections;
        this.customDomain = builder.customDomain;
        this.cdnDomain = builder.cdnDomain;
        this.maxRetries = builder.maxRetries;
        this.retryDelayMs = builder.retryDelayMs;
    }
    
    /**
     * 验证配置
     * 
     * 此方法检查所有必需字段是否存在且有效。
     * 应在使用配置初始化客户端之前调用。
     *
     * @throws IllegalArgumentException 如果任何验证规则失败
     */
    public void validate() {
        // 验证serverUrl
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException("serverUrl is required and cannot be blank");
        }
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            throw new IllegalArgumentException("serverUrl must start with http:// or https://");
        }
        
        // 验证tenantId
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required and cannot be blank");
        }
        if (!tenantId.matches("^[a-z0-9_-]+$")) {
            throw new IllegalArgumentException(
                "tenantId must contain only lowercase letters, numbers, underscores, and hyphens"
            );
        }
        
        // 验证tokenProvider
        if (tokenProvider == null) {
            throw new IllegalArgumentException("tokenProvider is required");
        }
        
        // 验证超时值
        if (connectTimeout <= 0) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        if (readTimeout <= 0) {
            throw new IllegalArgumentException("readTimeout must be positive");
        }
        
        // 验证连接池大小
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("maxConnections must be positive");
        }
        
        // 验证重试设置
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries cannot be negative");
        }
        if (retryDelayMs < 0) {
            throw new IllegalArgumentException("retryDelayMs cannot be negative");
        }
        
        // 如果提供了域名URL则进行验证
        if (customDomain != null && !customDomain.isBlank()) {
            if (!customDomain.startsWith("http://") && !customDomain.startsWith("https://")) {
                throw new IllegalArgumentException("customDomain must start with http:// or https://");
            }
        }
        if (cdnDomain != null && !cdnDomain.isBlank()) {
            if (!cdnDomain.startsWith("http://") && !cdnDomain.startsWith("https://")) {
                throw new IllegalArgumentException("cdnDomain must start with http:// or https://");
            }
        }
    }
    
    // Getter方法
    
    public String getServerUrl() {
        return serverUrl;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public TokenProvider getTokenProvider() {
        return tokenProvider;
    }
    
    public int getConnectTimeout() {
        return connectTimeout;
    }
    
    public int getReadTimeout() {
        return readTimeout;
    }
    
    public int getMaxConnections() {
        return maxConnections;
    }
    
    public String getCustomDomain() {
        return customDomain;
    }
    
    public String getCdnDomain() {
        return cdnDomain;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public long getRetryDelayMs() {
        return retryDelayMs;
    }
    
    /**
     * 创建新的构建器实例
     *
     * @return 新的Builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * FileServiceClientConfig的构建器类
     */
    public static class Builder {
        // 必需字段
        private String serverUrl;
        private String tenantId;
        private TokenProvider tokenProvider;
        
        // 可选字段及默认值
        private int connectTimeout = 10000;      // 10秒
        private int readTimeout = 30000;         // 30秒
        private int maxConnections = 50;
        private String customDomain;
        private String cdnDomain;
        private int maxRetries = 3;
        private long retryDelayMs = 1000;        // 1秒
        
        private Builder() {
        }
        
        /**
         * 设置文件服务服务器URL
         *
         * @param serverUrl 文件服务的基础URL（例如："http://localhost:8089"）
         * @return 此构建器
         */
        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }
        
        /**
         * 设置租户ID（应用程序ID）
         *
         * @param tenantId 租户/应用程序标识符
         * @return 此构建器
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        /**
         * 设置用于认证的令牌提供者
         *
         * @param tokenProvider 令牌提供者
         * @return 此构建器
         */
        public Builder tokenProvider(TokenProvider tokenProvider) {
            this.tokenProvider = tokenProvider;
            return this;
        }
        
        /**
         * 设置连接超时时间（毫秒）
         *
         * @param connectTimeout 超时时间（毫秒）（默认：10000）
         * @return 此构建器
         */
        public Builder connectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }
        
        /**
         * 设置读取超时时间（毫秒）
         *
         * @param readTimeout 超时时间（毫秒）（默认：30000）
         * @return 此构建器
         */
        public Builder readTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }
        
        /**
         * 设置连接池中的最大HTTP连接数
         *
         * @param maxConnections 最大连接数（默认：50）
         * @return 此构建器
         */
        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }
        
        /**
         * 设置文件URL的自定义域名
         * 
         * 设置后，服务器返回的文件URL将其域名替换为此自定义域名。
         *
         * @param customDomain 自定义域名（例如："https://files.example.com"）
         * @return 此构建器
         */
        public Builder customDomain(String customDomain) {
            this.customDomain = customDomain;
            return this;
        }
        
        /**
         * 设置公开文件URL的CDN域名
         * 
         * 设置后，公开文件URL将使用此CDN域名而不是自定义域名或服务器URL。
         *
         * @param cdnDomain CDN域名（例如："https://cdn.example.com"）
         * @return 此构建器
         */
        public Builder cdnDomain(String cdnDomain) {
            this.cdnDomain = cdnDomain;
            return this;
        }
        
        /**
         * 设置失败请求的最大重试次数
         *
         * @param maxRetries 最大重试次数（默认：3）
         * @return 此构建器
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        /**
         * 设置重试尝试之间的延迟时间（毫秒）
         *
         * @param retryDelayMs 延迟时间（毫秒）（默认：1000）
         * @return 此构建器
         */
        public Builder retryDelayMs(long retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
            return this;
        }
        
        /**
         * 构建FileServiceClientConfig实例
         *
         * @return 新的FileServiceClientConfig
         */
        public FileServiceClientConfig build() {
            return new FileServiceClientConfig(this);
        }
    }
}
