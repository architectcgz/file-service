package com.architectcgz.file.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for admin API authentication
 */
@Data
@Component
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {
    
    /**
     * List of API keys for admin authentication
     */
    private List<ApiKeyConfig> apiKeys = new ArrayList<>();
    
    /**
     * Configuration for a single API key
     */
    @Data
    public static class ApiKeyConfig {
        /**
         * Name/identifier for this API key
         */
        private String name;
        
        /**
         * The actual API key value
         */
        private String key;
        
        /**
         * Permissions associated with this API key
         */
        private List<String> permissions = new ArrayList<>();
    }
}
