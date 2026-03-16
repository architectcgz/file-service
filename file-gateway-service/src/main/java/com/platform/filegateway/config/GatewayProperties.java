package com.platform.filegateway.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private final Upstream upstream = new Upstream();
    private final Auth auth = new Auth();

    @Data
    public static class Upstream {

        @NotBlank
        private String baseUrl = "http://localhost:8089";

        private Duration connectTimeout = Duration.ofSeconds(3);

        private Duration readTimeout = Duration.ofSeconds(5);
    }

    @Data
    public static class Auth {

        /**
         * 是否允许直接信任 Header 身份。
         * V2 默认关闭，仅保留给受控的内部过渡环境。
         */
        private boolean allowHeaderIdentity = false;

        @NotBlank
        private String signingSecret = "change-me-before-production";
    }
}
