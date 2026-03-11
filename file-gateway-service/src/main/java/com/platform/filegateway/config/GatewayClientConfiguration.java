package com.platform.filegateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class GatewayClientConfiguration {

    @Bean
    RestClient fileServiceRestClient(RestClient.Builder builder, GatewayProperties gatewayProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(gatewayProperties.getUpstream().getConnectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(gatewayProperties.getUpstream().getReadTimeout());

        return builder
                .baseUrl(trimTrailingSlash(gatewayProperties.getUpstream().getBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
