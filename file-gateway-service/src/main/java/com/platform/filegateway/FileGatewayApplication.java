package com.platform.filegateway;

import com.platform.filegateway.config.GatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class FileGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileGatewayApplication.class, args);
    }
}
