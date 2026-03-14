package com.architectcgz.file.application.service.filetypevalidation.config;

import com.architectcgz.file.infrastructure.config.FileTypeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 文件类型规则配置服务。
 */
@Service
@RequiredArgsConstructor
public class FileTypeRuleConfigService {

    private final FileTypeProperties fileTypeProperties;

    public boolean isAllowedExtension(String extension) {
        return fileTypeProperties.isAllowedExtension(extension);
    }

    public boolean isAllowedContentType(String contentType) {
        return fileTypeProperties.isAllowedContentType(contentType);
    }

    public boolean isFileSizeAllowed(long fileSize) {
        return fileTypeProperties.isFileSizeAllowed(fileSize);
    }

    public long getMaxFileSize() {
        return fileTypeProperties.getMaxFileSize();
    }

    public boolean isMagicNumberCheckEnabled() {
        return fileTypeProperties.isEnableMagicNumberCheck();
    }
}
