package com.platform.fileservice.client;

import com.platform.fileservice.client.config.FileServiceClientConfig;
import com.platform.fileservice.client.config.TokenProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 资源管理测试
 * 
 * 验证FileServiceClient的资源管理功能，包括：
 * - close()方法的正确实现
 * - 关闭后的状态检查
 * - 幂等性（多次关闭是安全的）
 */
class FileServiceClientResourceManagementTest {
    
    /**
     * 测试客户端可以正常关闭
     */
    @Test
    void testClientCanBeClosed() {
        // 创建配置
        FileServiceClientConfig config = FileServiceClientConfig.builder()
                .serverUrl("http://localhost:8089")
                .tenantId("test-tenant")
                .tokenProvider(TokenProvider.fixed("test-token"))
                .build();
        
        // 创建客户端
        FileServiceClient client = new FileServiceClientImpl(config);
        
        // 关闭客户端（不应抛出异常）
        assertDoesNotThrow(() -> client.close());
    }
    
    /**
     * 测试关闭后的客户端不能再使用
     */
    @Test
    void testClosedClientThrowsException() {
        // 创建配置
        FileServiceClientConfig config = FileServiceClientConfig.builder()
                .serverUrl("http://localhost:8089")
                .tenantId("test-tenant")
                .tokenProvider(TokenProvider.fixed("test-token"))
                .build();
        
        // 创建并关闭客户端
        FileServiceClient client = new FileServiceClientImpl(config);
        client.close();
        
        // 尝试使用已关闭的客户端应该抛出IllegalStateException
        assertThrows(IllegalStateException.class, () -> {
            client.getFileUrl("test-file-id");
        });
    }
    
    /**
     * 测试多次关闭是安全的（幂等性）
     */
    @Test
    void testMultipleCloseIsSafe() {
        // 创建配置
        FileServiceClientConfig config = FileServiceClientConfig.builder()
                .serverUrl("http://localhost:8089")
                .tenantId("test-tenant")
                .tokenProvider(TokenProvider.fixed("test-token"))
                .build();
        
        // 创建客户端
        FileServiceClient client = new FileServiceClientImpl(config);
        
        // 多次关闭不应抛出异常
        assertDoesNotThrow(() -> {
            client.close();
            client.close();
            client.close();
        });
    }
    
    /**
     * 测试try-with-resources自动关闭
     */
    @Test
    void testTryWithResourcesAutoClose() {
        // 创建配置
        FileServiceClientConfig config = FileServiceClientConfig.builder()
                .serverUrl("http://localhost:8089")
                .tenantId("test-tenant")
                .tokenProvider(TokenProvider.fixed("test-token"))
                .build();
        
        // 使用try-with-resources（应该自动关闭）
        assertDoesNotThrow(() -> {
            try (FileServiceClient client = new FileServiceClientImpl(config)) {
                // 客户端在这里可以正常使用
                assertNotNull(client);
            }
            // 客户端应该已经自动关闭
        });
    }
}
