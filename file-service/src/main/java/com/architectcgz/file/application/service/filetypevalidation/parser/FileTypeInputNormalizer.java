package com.architectcgz.file.application.service.filetypevalidation.parser;

import org.springframework.stereotype.Component;

/**
 * 文件类型输入归一化器。
 */
@Component
public class FileTypeInputNormalizer {

    public String extractExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }

    public String normalizeContentType(String contentType) {
        return contentType.split(";")[0].trim();
    }
}
