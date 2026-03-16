package com.architectcgz.file.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Knife4j configuration aligned with the ZhiCore services.
 */
@Configuration
public class OpenApiConfig {

    private static final String LEGACY_CONTROLLER_PACKAGE = "com.architectcgz.file.interfaces.controller";
    private static final String CORE_WEB_CONTROLLER_PACKAGE = "com.platform.fileservice.core.web.controller";

    @Bean
    public OpenAPI customOpenAPI(
            @Value("${knife4j.title:File Service API 文档}") String title,
            @Value("${knife4j.version:1.0.0}") String version,
            @Value("${knife4j.description:统一文件上传、访问控制与内容分发接口文档}") String description) {
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .version(version)
                        .description(description)
                        .contact(new Contact()
                                .name("File Platform Team")
                                .email("dev@platform.com")))
                .components(new Components()
                        .addSecuritySchemes("x-app-id",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-App-Id")
                                        .description("租户 / 应用标识"))
                        .addSecuritySchemes("x-user-id",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-User-Id")
                                        .description("用户标识，按接口要求填写"))
                        .addSecuritySchemes("x-admin-api-key",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Admin-Api-Key")
                                        .description("管理员接口访问凭证")));
    }

    @Bean
    public GroupedOpenApi allApis() {
        return GroupedOpenApi.builder()
                .group("全部接口")
                .pathsToMatch("/api/**")
                .packagesToScan(LEGACY_CONTROLLER_PACKAGE, CORE_WEB_CONTROLLER_PACKAGE)
                .build();
    }

    @Bean
    public GroupedOpenApi uploadEntryApis() {
        return GroupedOpenApi.builder()
                .group("上传入口")
                .pathsToMatch(
                        "/api/v1/upload/**",
                        "/api/v1/direct-upload/**",
                        "/api/v1/multipart/**"
                )
                .packagesToScan(LEGACY_CONTROLLER_PACKAGE)
                .build();
    }

    @Bean
    public GroupedOpenApi uploadSessionApis() {
        return GroupedOpenApi.builder()
                .group("上传会话")
                .pathsToMatch("/api/v1/upload-sessions", "/api/v1/upload-sessions/**")
                .packagesToScan(CORE_WEB_CONTROLLER_PACKAGE)
                .build();
    }

    @Bean
    public GroupedOpenApi fileAccessApis() {
        return GroupedOpenApi.builder()
                .group("文件访问")
                .pathsToMatch("/api/v1/files*", "/api/v1/files/**")
                .packagesToScan(CORE_WEB_CONTROLLER_PACKAGE)
                .build();
    }

    @Bean
    public GroupedOpenApi adminApis() {
        return GroupedOpenApi.builder()
                .group("管理接口")
                .pathsToMatch("/api/v1/admin/**")
                .packagesToScan(LEGACY_CONTROLLER_PACKAGE)
                .build();
    }
}
