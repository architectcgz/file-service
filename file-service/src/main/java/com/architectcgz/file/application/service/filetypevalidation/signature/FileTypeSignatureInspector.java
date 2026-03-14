package com.architectcgz.file.application.service.filetypevalidation.signature;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.infrastructure.util.FileTypeDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文件签名识别器。
 */
@Slf4j
@Component
public class FileTypeSignatureInspector {

    public String detectFileTypeOrThrow(String fileName, byte[] fileHeader) {
        if (fileHeader == null || fileHeader.length < 4) {
            log.warn("文件头数据不足，无法进行魔数检测");
            throw new BusinessException(
                    FileServiceErrorCodes.FILE_HEADER_INSUFFICIENT,
                    FileServiceErrorMessages.FILE_HEADER_INSUFFICIENT
            );
        }

        String detectedType = FileTypeDetector.detectFileType(fileHeader);
        if (detectedType == null) {
            log.warn("无法识别文件类型，文件名: {}", fileName);
            throw new BusinessException(
                    FileServiceErrorCodes.FILE_TYPE_UNRECOGNIZED,
                    FileServiceErrorMessages.FILE_TYPE_UNRECOGNIZED
            );
        }
        return detectedType;
    }

    public void requireTypeMatch(String declaredContentType, String detectedType, String fileName) {
        if (!FileTypeDetector.isTypeMatch(declaredContentType, detectedType)) {
            log.warn("文件类型不匹配: 声明={}, 检测={}, 文件名={}",
                    declaredContentType, detectedType, fileName);
            throw new BusinessException(
                    FileServiceErrorCodes.FILE_TYPE_CONTENT_MISMATCH,
                    FileServiceErrorMessages.FILE_TYPE_CONTENT_MISMATCH
            );
        }
    }
}
