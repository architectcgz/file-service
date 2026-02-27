package com.architectcgz.file;

import com.architectcgz.file.infrastructure.config.AdminProperties;
import com.architectcgz.file.infrastructure.config.CacheProperties;
import org.mybatis.spring.annotation.MapperScan;
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
@MapperScan("com.architectcgz.file.infrastructure.repository.mapper")
@EnableScheduling
@EnableConfigurationProperties({CacheProperties.class, AdminProperties.class})
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
