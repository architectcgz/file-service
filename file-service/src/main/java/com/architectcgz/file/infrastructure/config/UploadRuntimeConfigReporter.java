package com.architectcgz.file.infrastructure.config;

import com.platform.fileservice.core.web.config.FileCoreUploadProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * 上传运行时配置收口日志。
 *
 * 启动时打印真正生效的关键阈值，并对常见冲突做预警。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadRuntimeConfigReporter {

    private final FileTypeProperties fileTypeProperties;
    private final MultipartProperties multipartProperties;
    private final UploadDedupProperties uploadDedupProperties;
    private final FileCoreUploadProperties fileCoreUploadProperties;

    @Value("${spring.servlet.multipart.max-file-size}")
    private DataSize multipartMaxFileSize;

    @Value("${spring.servlet.multipart.max-request-size}")
    private DataSize multipartMaxRequestSize;

    @PostConstruct
    void logEffectiveConfig() {
        long declaredMaxFileSize = fileTypeProperties.getMaxFileSize();
        long multipartThreshold = multipartProperties.getThreshold();
        long autoSingleMaxSize = fileCoreUploadProperties.getAutoPresignedSingleMaxSizeBytes();

        log.info(
                "Effective upload runtime config: multipartMaxFileSize={}B, multipartMaxRequestSize={}B, " +
                        "declaredMaxFileSize={}B, proxyMultipartThreshold={}B, autoSingleMaxSize={}B, " +
                        "dedupNotificationMaxWait={}, dedupRenewSchedulerThreads={}",
                multipartMaxFileSize.toBytes(),
                multipartMaxRequestSize.toBytes(),
                declaredMaxFileSize,
                multipartThreshold,
                autoSingleMaxSize,
                uploadDedupProperties.getNotificationMaxWait(),
                uploadDedupProperties.getRenewSchedulerThreads()
        );

        if (multipartMaxFileSize.toBytes() < declaredMaxFileSize) {
            log.warn("Multipart max-file-size is smaller than declared file max size: multipart={}B, declared={}B",
                    multipartMaxFileSize.toBytes(), declaredMaxFileSize);
        }
        if (multipartMaxRequestSize.toBytes() < multipartMaxFileSize.toBytes()) {
            log.warn("Multipart max-request-size is smaller than multipart max-file-size: request={}B, file={}B",
                    multipartMaxRequestSize.toBytes(), multipartMaxFileSize.toBytes());
        }
        if (autoSingleMaxSize > multipartMaxRequestSize.toBytes()) {
            log.warn("Auto single-upload threshold is larger than multipart request limit: autoSingle={}B, request={}B",
                    autoSingleMaxSize, multipartMaxRequestSize.toBytes());
        }
        if (autoSingleMaxSize > declaredMaxFileSize) {
            log.warn("Auto single-upload threshold is larger than declared file max size: autoSingle={}B, declared={}B",
                    autoSingleMaxSize, declaredMaxFileSize);
        }
    }
}
