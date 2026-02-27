package com.platform.fileservice.starter;

import com.platform.fileservice.client.config.TokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Spring集成的TokenProvider实现
 * 
 * 此实现尝试从Spring Security上下文获取JWT令牌。
 * 如果Spring Security不可用或未配置，则回退到配置的静态令牌。
 * 
 * 支持的Spring Security配置：
 * - JwtAuthenticationToken（OAuth2资源服务器）
 * - 其他包含JWT令牌的Authentication实现
 * 
 * 回退顺序：
 * 1. 尝试从Spring Security上下文获取JWT令牌
 * 2. 如果失败，使用配置的静态令牌
 * 3. 如果静态令牌也不可用，抛出异常
 *
 * @author File Service Team
 */
public class SpringTokenProvider implements TokenProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(SpringTokenProvider.class);
    
    private final String staticToken;
    private static final boolean SPRING_SECURITY_AVAILABLE;
    
    static {
        boolean available = false;
        try {
            Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            available = true;
        } catch (ClassNotFoundException e) {
            // Spring Security not available
        }
        SPRING_SECURITY_AVAILABLE = available;
    }
    
    /**
     * 创建SpringTokenProvider实例
     *
     * @param staticToken 静态令牌作为回退（可以为null）
     */
    public SpringTokenProvider(String staticToken) {
        this.staticToken = staticToken;
    }
    
    /**
     * 获取认证令牌
     * 
     * 首先尝试从Spring Security上下文获取令牌，
     * 如果失败则回退到静态令牌。
     *
     * @return JWT令牌（不带"Bearer "前缀）
     * @throws IllegalStateException 如果无法获取令牌
     */
    @Override
    public String getToken() {
        // 尝试从Spring Security上下文获取令牌
        if (SPRING_SECURITY_AVAILABLE) {
            String tokenFromContext = getTokenFromSecurityContext();
            if (tokenFromContext != null) {
                logger.debug("使用来自Spring Security上下文的令牌");
                return tokenFromContext;
            }
        }
        
        // 回退到静态令牌
        if (staticToken != null && !staticToken.isBlank()) {
            logger.debug("使用配置的静态令牌");
            return staticToken;
        }
        
        // 两者都不可用
        throw new IllegalStateException(
            "无法获取认证令牌：Spring Security上下文中没有令牌，且未配置静态令牌。" +
            "请配置file-service.client.token属性或确保Spring Security已正确设置。"
        );
    }
    
    /**
     * 尝试从Spring Security上下文获取JWT令牌
     * 使用反射以避免在Spring Security不可用时出现ClassNotFoundException
     *
     * @return JWT令牌字符串，如果不可用则返回null
     */
    private String getTokenFromSecurityContext() {
        try {
            // 使用反射获取SecurityContextHolder
            Class<?> securityContextHolderClass = Class.forName(
                "org.springframework.security.core.context.SecurityContextHolder");
            Method getContextMethod = securityContextHolderClass.getMethod("getContext");
            Object securityContext = getContextMethod.invoke(null);
            
            if (securityContext == null) {
                return null;
            }
            
            // 获取Authentication
            Method getAuthenticationMethod = securityContext.getClass().getMethod("getAuthentication");
            Object authentication = getAuthenticationMethod.invoke(securityContext);
            
            if (authentication == null) {
                logger.trace("Spring Security上下文中没有认证信息");
                return null;
            }
            
            // 尝试获取JWT令牌
            // 1. 检查是否是JwtAuthenticationToken
            if (authentication.getClass().getName().contains("JwtAuthenticationToken")) {
                Method getTokenMethod = authentication.getClass().getMethod("getToken");
                Object jwt = getTokenMethod.invoke(authentication);
                if (jwt != null) {
                    Method getTokenValueMethod = jwt.getClass().getMethod("getTokenValue");
                    return (String) getTokenValueMethod.invoke(jwt);
                }
            }
            
            // 2. 尝试从credentials获取
            Method getCredentialsMethod = authentication.getClass().getMethod("getCredentials");
            Object credentials = getCredentialsMethod.invoke(authentication);
            
            if (credentials != null) {
                // 检查credentials是否是Jwt
                if (credentials.getClass().getName().contains("Jwt")) {
                    Method getTokenValueMethod = credentials.getClass().getMethod("getTokenValue");
                    return (String) getTokenValueMethod.invoke(credentials);
                }
                
                // 如果credentials是字符串，假设它是令牌
                if (credentials instanceof String token && !token.isBlank()) {
                    // 移除"Bearer "前缀（如果存在）
                    if (token.startsWith("Bearer ")) {
                        return token.substring(7);
                    }
                    return token;
                }
            }
            
            logger.trace("Spring Security认证不包含JWT令牌");
            return null;
            
        } catch (Exception e) {
            // 如果Spring Security不可用或发生错误，记录并返回null
            logger.trace("从Spring Security上下文获取令牌时出错：{}", e.getMessage());
            return null;
        }
    }
}
