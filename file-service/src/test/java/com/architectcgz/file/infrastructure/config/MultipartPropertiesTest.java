package com.architectcgz.file.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MultipartProperties 配置测试
 */
@SpringBootTest(
    classes = MultipartProperties.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "storage.multipart.enabled=true",
    "storage.multipart.threshold=10485760",
    "storage.multipart.chunk-size=5242880",
    "storage.multipart.max-parts=10000",
    "storage.multipart.task-expire-hours=24",
    "storage.multipart.cleanup-cron=0 0 * * * *",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.config.import=",
    "spring.autoconfigure.exclude=org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class MultipartPropertiesTest {

    @Autowired
    private MultipartProperties multipartProperties;

    @Test
    void testMultipartPropertiesLoaded() {
        assertNotNull(multipartProperties, "MultipartProperties should be loaded");
        
        assertTrue(multipartProperties.isEnabled(), "Multipart should be enabled");
        assertEquals(10485760L, multipartProperties.getThreshold(), "Threshold should be 10MB");
        assertEquals(5242880, multipartProperties.getChunkSize(), "Chunk size should be 5MB");
        assertEquals(10000, multipartProperties.getMaxParts(), "Max parts should be 10000");
        assertEquals(24, multipartProperties.getTaskExpireHours(), "Task expire hours should be 24");
        assertEquals("0 0 * * * *", multipartProperties.getCleanupCron(), "Cleanup cron should be '0 0 * * * *'");
    }

    @Test
    void testDefaultValues() {
        // 测试默认值是否正确设或
        MultipartProperties props = new MultipartProperties();
        
        assertTrue(props.isEnabled(), "Default enabled should be true");
        assertEquals(10485760L, props.getThreshold(), "Default threshold should be 10MB");
        assertEquals(5242880, props.getChunkSize(), "Default chunk size should be 5MB");
        assertEquals(10000, props.getMaxParts(), "Default max parts should be 10000");
        assertEquals(24, props.getTaskExpireHours(), "Default task expire hours should be 24");
        assertEquals("0 0 * * * *", props.getCleanupCron(), "Default cleanup cron should be '0 0 * * * *'");
    }
}
