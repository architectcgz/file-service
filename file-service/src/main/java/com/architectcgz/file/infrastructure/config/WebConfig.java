package com.architectcgz.file.infrastructure.config;

import com.architectcgz.file.infrastructure.filter.ApiKeyAuthFilter;
import com.architectcgz.file.infrastructure.interceptor.AppIdValidationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置 - 本地文件访问和拦截器注册
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    
    private final AppIdValidationInterceptor appIdValidationInterceptor;
    private final ApiKeyAuthFilter apiKeyAuthFilter;
    
    @Value("${storage.local.base-path:./uploads}")
    private String basePath;
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置静态资源访问路径（仅在本地存储模式下）
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + basePath + "/");
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 App ID 验证拦截器到所有 API 路径
        registry.addInterceptor(appIdValidationInterceptor)
                .addPathPatterns("/api/v1/**")
                .order(1); // 设置为最高优先级
    }
    
    /**
     * 注册 API Key 认证过滤器
     * 用于管理员 API 的认证
     */
    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilterRegistration() {
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(apiKeyAuthFilter);
        registration.addUrlPatterns("/api/v1/admin/*");
        registration.setOrder(0); // 设置为最高优先级，在拦截器之前执行
        registration.setName("apiKeyAuthFilter");
        return registration;
    }
}
