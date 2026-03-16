package com.architectcgz.file.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多实例部署安全校验开关。
 */
@Data
@ConfigurationProperties(prefix = "file-service.deployment")
public class MultiInstanceDeploymentProperties {

    /**
     * 启用后，在启动阶段强制校验多实例部署前提。
     */
    private boolean multiInstanceEnabled = false;
}
