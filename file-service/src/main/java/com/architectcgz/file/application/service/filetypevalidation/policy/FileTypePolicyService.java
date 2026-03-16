package com.architectcgz.file.application.service.filetypevalidation.policy;

import com.architectcgz.file.application.service.filetypevalidation.config.FileTypeRuleConfigService;
import com.architectcgz.file.application.service.filetypevalidation.parser.FileTypeInputNormalizer;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件类型策略校验服务。
 *
 * 负责扩展名、Content-Type、大小等声明侧校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileTypePolicyService {

    private final FileTypeRuleConfigService fileTypeRuleConfigService;
    private final FileTypeInputNormalizer fileTypeInputNormalizer;

    public void validateFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            throw new BusinessException(FileServiceErrorCodes.FILENAME_EMPTY, FileServiceErrorMessages.FILENAME_EMPTY);
        }

        String extension = fileTypeInputNormalizer.extractExtension(fileName);
        if (extension == null || extension.isEmpty()) {
            throw new BusinessException(FileServiceErrorCodes.EXTENSION_REQUIRED, FileServiceErrorMessages.EXTENSION_REQUIRED);
        }

        if (!fileTypeRuleConfigService.isAllowedExtension(extension)) {
            log.warn("不支持的文件扩展名: {}", extension);
            throw new BusinessException(
                    FileServiceErrorCodes.EXTENSION_NOT_ALLOWED,
                    String.format(FileServiceErrorMessages.EXTENSION_NOT_ALLOWED, extension)
            );
        }
        log.debug("文件扩展名验证通过: {}", extension);
    }

    public void validateContentType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            throw new BusinessException(FileServiceErrorCodes.CONTENT_TYPE_EMPTY, FileServiceErrorMessages.CONTENT_TYPE_EMPTY);
        }

        String mimeType = fileTypeInputNormalizer.normalizeContentType(contentType);
        if (!fileTypeRuleConfigService.isAllowedContentType(mimeType)) {
            log.warn("不支持的文件类型: {}", mimeType);
            throw new BusinessException(
                    FileServiceErrorCodes.CONTENT_TYPE_NOT_ALLOWED,
                    String.format(FileServiceErrorMessages.CONTENT_TYPE_NOT_ALLOWED, mimeType)
            );
        }
        log.debug("Content-Type 验证通过: {}", mimeType);
    }

    public void validateFileSize(long fileSize) {
        if (fileSize <= 0) {
            throw new BusinessException(
                    FileServiceErrorCodes.FILE_SIZE_MUST_POSITIVE,
                    FileServiceErrorMessages.FILE_SIZE_MUST_POSITIVE
            );
        }

        if (!fileTypeRuleConfigService.isFileSizeAllowed(fileSize)) {
            long maxSizeMB = fileTypeRuleConfigService.getMaxFileSize() / (1024 * 1024);
            log.warn("文件大小超出限制: {} bytes (最大 {} MB)", fileSize, maxSizeMB);
            throw new BusinessException(
                    FileServiceErrorCodes.FILE_SIZE_EXCEEDED,
                    String.format(FileServiceErrorMessages.FILE_SIZE_EXCEEDED, maxSizeMB)
            );
        }
        log.debug("文件大小验证通过: {} bytes", fileSize);
    }

    public void validateFile(String fileName, String contentType, long fileSize) {
        validateFileExtension(fileName);
        validateContentType(contentType);
        validateFileSize(fileSize);
        log.debug("文件验证通过: fileName={}, contentType={}, size={}", fileName, contentType, fileSize);
    }
}
