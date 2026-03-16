package com.architectcgz.file.application.service.upload.command;

import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.UploadTransactionHelper;
import com.architectcgz.file.application.service.upload.UploadDedupCoordinatorService;
import com.architectcgz.file.application.service.upload.factory.UploadObjectFactory;
import com.architectcgz.file.application.service.upload.file.PreparedUploadSource;
import com.architectcgz.file.application.service.upload.file.UploadTempFileService;
import com.architectcgz.file.application.service.upload.storage.UploadStorageService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.model.UploadFile;
import com.architectcgz.file.domain.service.TenantDomainService;
import com.architectcgz.file.interfaces.dto.UploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadCommandService {

    private static final String TEMP_FILE_PREFIX = "file-upload-";

    private final UploadObjectFactory uploadObjectFactory;
    private final UploadStorageService uploadStorageService;
    private final UploadDedupCoordinatorService uploadDedupCoordinatorService;
    private final UploadTempFileService uploadTempFileService;
    private final FileTypeValidator fileTypeValidator;
    private final TenantDomainService tenantDomainService;
    private final UploadTransactionHelper uploadTransactionHelper;

    public UploadResult uploadFile(String appId, MultipartFile file, String userId) {
        Path tempSourceFile = null;
        List<String> uploadedStoragePaths = new ArrayList<>();

        try {
            tenantDomainService.validateUploadPrerequisites(appId, file.getSize());

            PreparedUploadSource preparedUploadSource = uploadTempFileService.prepareMultipartFile(
                    file,
                    TEMP_FILE_PREFIX,
                    "." + uploadObjectFactory.resolveExtension(file.getOriginalFilename()),
                    12,
                    true
            );
            tempSourceFile = preparedUploadSource.file();

            fileTypeValidator.validateFileWithMagicNumber(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    preparedUploadSource.header(),
                    file.getSize()
            );

            String fileHash = preparedUploadSource.hash();
            String targetBucketName = uploadStorageService.resolveUploadBucketName();
            Path uploadTempSourceFile = tempSourceFile;
            return uploadDedupCoordinatorService.executeWithDedupClaim(
                    appId,
                    fileHash,
                    targetBucketName,
                    storageObject -> handleInstantUpload(appId, userId, file, fileHash, storageObject),
                    () -> handleNewUpload(appId, userId, file, uploadTempSourceFile, fileHash, targetBucketName, uploadedStoragePaths)
            );
        } catch (IOException e) {
            log.error("Failed to upload file: {}", file.getOriginalFilename(), e);
            throw new BusinessException(
                    FileServiceErrorCodes.FILE_UPLOAD_FAILED,
                    String.format(FileServiceErrorMessages.FILE_UPLOAD_FAILED, e.getMessage())
            );
        } finally {
            uploadTempFileService.deleteQuietly(tempSourceFile);
        }
    }

    private UploadResult handleInstantUpload(String appId, String userId, MultipartFile file,
                                             String fileHash, StorageObject storageObject) {
        String fileRecordId = uploadObjectFactory.generateFileId();
        var fileRecord = uploadObjectFactory.buildFileRecord(
                fileRecordId,
                appId,
                userId,
                storageObject.getId(),
                storageObject.getStoragePath(),
                file.getOriginalFilename(),
                file.getSize(),
                file.getContentType(),
                fileHash
        );
        uploadTransactionHelper.saveInstantUpload(storageObject.getId(), fileRecord, file.getSize());

        String fileUrl = uploadStorageService.resolvePublicUrl(
                storageObject.getBucketName(),
                storageObject.getStoragePath()
        );

        log.debug("File instant upload (deduplication): fileHash={}, userId={}, originalFilename={}",
                fileHash, userId, file.getOriginalFilename());

        return uploadObjectFactory.buildUploadResult(
                fileRecordId,
                fileUrl,
                null,
                file.getOriginalFilename(),
                file.getSize(),
                UploadFile.FileType.DOCUMENT,
                file.getContentType()
        );
    }

    private UploadResult handleNewUpload(String appId, String userId, MultipartFile file,
                                         Path tempSourceFile, String fileHash, String targetBucketName,
                                         List<String> uploadedStoragePaths) {
        String filePath = uploadObjectFactory.generateStoragePath(appId, userId, "files", file.getOriginalFilename());
        String fileUrl = uploadStorageService.uploadTempFile(tempSourceFile, filePath, file.getContentType());
        uploadedStoragePaths.add(filePath);

        String storageObjectId = uploadObjectFactory.generateFileId();
        var storageObject = uploadObjectFactory.buildStorageObject(
                appId, storageObjectId, fileHash, filePath, file.getSize(), file.getContentType(), targetBucketName
        );

        String fileRecordId = uploadObjectFactory.generateFileId();
        var fileRecord = uploadObjectFactory.buildFileRecord(
                fileRecordId, appId, userId, storageObjectId, filePath,
                file.getOriginalFilename(), file.getSize(), file.getContentType(), fileHash
        );

        try {
            uploadTransactionHelper.saveNewUpload(storageObject, fileRecord, file.getSize());
        } catch (Exception dbEx) {
            uploadStorageService.cleanupUploadedPaths(uploadedStoragePaths);
            throw dbEx;
        }

        log.debug("File uploaded successfully: filePath={}, userId={}, originalFilename={}",
                filePath, userId, file.getOriginalFilename());

        return uploadObjectFactory.buildUploadResult(
                fileRecordId,
                fileUrl,
                null,
                file.getOriginalFilename(),
                file.getSize(),
                UploadFile.FileType.DOCUMENT,
                file.getContentType()
        );
    }
}
