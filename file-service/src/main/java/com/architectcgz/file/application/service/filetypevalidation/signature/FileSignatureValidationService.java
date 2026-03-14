package com.architectcgz.file.application.service.filetypevalidation.signature;

import com.architectcgz.file.application.service.filetypevalidation.config.FileTypeRuleConfigService;
import com.architectcgz.file.application.service.filetypevalidation.policy.FileTypePolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件签名校验服务。
 *
 * 在策略校验通过后，按配置决定是否进行魔数检测。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileSignatureValidationService {

    private final FileTypeRuleConfigService fileTypeRuleConfigService;
    private final FileTypeSignatureInspector fileTypeSignatureInspector;
    private final FileTypePolicyService fileTypePolicyService;

    public void validateFileWithMagicNumber(String fileName, String contentType,
                                            byte[] fileHeader, long fileSize) {
        fileTypePolicyService.validateFile(fileName, contentType, fileSize);

        if (!fileTypeRuleConfigService.isMagicNumberCheckEnabled()) {
            log.debug("魔数检测已禁用，跳过文件类型检测");
            return;
        }

        String detectedType = fileTypeSignatureInspector.detectFileTypeOrThrow(fileName, fileHeader);
        fileTypeSignatureInspector.requireTypeMatch(contentType, detectedType, fileName);

        log.info("文件验证通过（含魔数检测）: fileName={}, contentType={}, detectedType={}, size={}",
                fileName, contentType, detectedType, fileSize);
    }
}
