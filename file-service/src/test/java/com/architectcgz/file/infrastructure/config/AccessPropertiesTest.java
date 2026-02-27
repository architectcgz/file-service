package com.architectcgz.file.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AccessProperties 配置属性测或
 */
@SpringBootTest(
    classes = AccessProperties.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@EnableConfigurationProperties(AccessProperties.class)
@TestPropertySource(properties = {
    "storage.access.private-url-expire-seconds=7200",
    "storage.access.presigned-url-expire-seconds=1800",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.config.import=",
    "spring.autoconfigure.exclude=org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class AccessPropertiesTest {
    
    @Autowired
    private AccessProperties accessProperties;
    
    @Test
    void testDefaultValues() {
        // 验证配置属性被正确注入
        assertNotNull(accessProperties);
    }
    
    @Test
    void testPrivateUrlExpireSeconds() {
        // 验证私有文件 URL 过期时间配置
        assertEquals(7200, accessProperties.getPrivateUrlExpireSeconds());
    }
    
    @Test
    void testPresignedUrlExpireSeconds() {
        // 验证预签名上传URL 过期时间配置
        assertEquals(1800, accessProperties.getPresignedUrlExpireSeconds());
    }
}
