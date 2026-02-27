package com.platform.fileservice.client.config;

import java.util.function.Supplier;

/**
 * 为文件服务客户端提供认证令牌的接口
 * 
 * 此接口允许灵活的令牌管理，支持静态令牌和从各种来源动态检索令牌
 * （例如：Spring Security上下文、令牌刷新服务等）。
 *
 * @author File Service Team
 */
@FunctionalInterface
public interface TokenProvider {
    
    /**
     * 获取当前认证令牌
     * 
     * 令牌应该不带"Bearer "前缀返回。
     * 客户端在构造Authorization请求头时会自动添加"Bearer "前缀。
     *
     * @return JWT令牌字符串（不带"Bearer "前缀）
     */
    String getToken();
    
    /**
     * 创建始终返回相同固定令牌的TokenProvider
     * 
     * 这对于令牌不变的简单用例很有用，
     * 例如使用长期令牌的服务间认证。
     *
     * @param token 要返回的固定令牌（不带"Bearer "前缀）
     * @return 始终返回给定令牌的TokenProvider
     * @throws IllegalArgumentException 如果token为null或空白
     */
    static TokenProvider fixed(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or blank");
        }
        return () -> token;
    }
    
    /**
     * 从Supplier函数创建TokenProvider
     * 
     * 这对于动态令牌检索很有用，例如：
     * - 从Spring Security上下文获取令牌
     * - 从令牌服务刷新令牌
     * - 从外部配置读取令牌
     *
     * @param supplier 提供令牌的供应商函数
     * @return 委托给供应商的TokenProvider
     * @throws IllegalArgumentException 如果supplier为null
     */
    static TokenProvider fromSupplier(Supplier<String> supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("Supplier cannot be null");
        }
        return supplier::get;
    }
}
