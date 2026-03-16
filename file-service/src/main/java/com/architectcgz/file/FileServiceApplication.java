package com.architectcgz.file;

import com.architectcgz.file.infrastructure.config.AdminProperties;
import com.architectcgz.file.infrastructure.config.CacheProperties;
import com.architectcgz.file.infrastructure.config.CleanupProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 文件服务启动类
 * 
 * 独立的文件服务，不依赖 Spring Cloud
 *
 * @author Blog Team
 */
@SpringBootApplication(scanBasePackages = {"com.architectcgz.file", "com.platform.file"})
@EnableScheduling
@EnableConfigurationProperties({CacheProperties.class, AdminProperties.class, CleanupProperties.class})
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
