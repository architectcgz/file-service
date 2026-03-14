package com.architectcgz.file.application.service;

import com.architectcgz.file.application.service.filetypevalidation.config.FileTypeRuleConfigService;
import com.architectcgz.file.application.service.filetypevalidation.parser.FileTypeInputNormalizer;
import com.architectcgz.file.application.service.filetypevalidation.policy.FileTypePolicyService;
import com.architectcgz.file.application.service.filetypevalidation.signature.FileSignatureValidationService;
import com.architectcgz.file.application.service.filetypevalidation.signature.FileTypeSignatureInspector;
import com.architectcgz.file.infrastructure.config.FileTypeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 文件类型验证门面。
 *
 * 对外保留原有校验入口，内部拆分为策略校验和文件签名校验。
 */
@Service
public class FileTypeValidator {

    private final FileTypePolicyService fileTypePolicyService;
    private final FileSignatureValidationService fileSignatureValidationService;

    @Autowired
    public FileTypeValidator(FileTypePolicyService fileTypePolicyService,
                             FileSignatureValidationService fileSignatureValidationService) {
        this.fileTypePolicyService = fileTypePolicyService;
        this.fileSignatureValidationService = fileSignatureValidationService;
    }

    FileTypeValidator(FileTypeProperties fileTypeProperties) {
        FileTypeRuleConfigService ruleConfigService = new FileTypeRuleConfigService(fileTypeProperties);
        FileTypePolicyService policyService = new FileTypePolicyService(
                ruleConfigService,
                new FileTypeInputNormalizer()
        );
        this.fileTypePolicyService = policyService;
        this.fileSignatureValidationService = new FileSignatureValidationService(
                ruleConfigService,
                new FileTypeSignatureInspector(),
                policyService
        );
    }

    /**
     * 验证文件扩展名。
     *
     * @param fileName 文件名
     */
    public void validateFileExtension(String fileName) {
        fileTypePolicyService.validateFileExtension(fileName);
    }

    /**
     * 验证 Content-Type。
     *
     * @param contentType Content-Type 值
     */
    public void validateContentType(String contentType) {
        fileTypePolicyService.validateContentType(contentType);
    }

    /**
     * 验证文件大小。
     *
     * @param fileSize 文件大小（字节）
     */
    public void validateFileSize(long fileSize) {
        fileTypePolicyService.validateFileSize(fileSize);
    }

    /**
     * 完整验证文件策略。
     *
     * @param fileName 文件名
     * @param contentType Content-Type 值
     * @param fileSize 文件大小（字节）
     */
    public void validateFile(String fileName, String contentType, long fileSize) {
        fileTypePolicyService.validateFile(fileName, contentType, fileSize);
    }

    /**
     * 完整验证文件并进行魔数检测。
     *
     * @param fileName 文件名
     * @param contentType Content-Type 值
     * @param fileHeader 文件头字节数组
     * @param fileSize 文件大小（字节）
     */
    public void validateFileWithMagicNumber(String fileName, String contentType,
                                            byte[] fileHeader, long fileSize) {
        fileSignatureValidationService.validateFileWithMagicNumber(fileName, contentType, fileHeader, fileSize);
    }
}
