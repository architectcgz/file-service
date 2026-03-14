package com.architectcgz.file.application.service.upload.command;

import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.UploadTransactionHelper;
import com.architectcgz.file.application.service.upload.factory.UploadObjectFactory;
import com.architectcgz.file.application.service.upload.file.UploadFileHashService;
import com.architectcgz.file.application.service.upload.storage.UploadStorageService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.model.UploadFile;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.service.TenantDomainService;
import com.architectcgz.file.interfaces.dto.UploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadCommandService {

    private final UploadObjectFactory uploadObjectFactory;
    private final UploadStorageService uploadStorageService;
    private final UploadFileHashService uploadFileHashService;
    private final StorageObjectRepository storageObjectRepository;
    private final FileTypeValidator fileTypeValidator;
    private final TenantDomainService tenantDomainService;
    private final UploadTransactionHelper uploadTransactionHelper;

    public UploadResult uploadFile(String appId, MultipartFile file, String userId) {
        try {
            tenantDomainService.checkQuota(appId, file.getSize());

            byte[] fileData = file.getBytes();
            byte[] fileHeader = new byte[Math.min(12, fileData.length)];
            System.arraycopy(fileData, 0, fileHeader, 0, fileHeader.length);

            fileTypeValidator.validateFileWithMagicNumber(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    fileHeader,
                    file.getSize()
            );

            String fileHash = uploadFileHashService.calculateHash(fileData);
            String targetBucketName = uploadStorageService.resolveUploadBucketName();
            Optional<StorageObject> existingStorageObject = storageObjectRepository.findByFileHashAndBucket(
                    appId, fileHash, targetBucketName
            );

            if (existingStorageObject.isPresent()) {
                return handleInstantUpload(appId, userId, file, fileHash, existingStorageObject.get());
            }

            return handleNewUpload(appId, userId, file, fileData, fileHash, targetBucketName);
        } catch (IOException e) {
            log.error("Failed to upload file: {}", file.getOriginalFilename(), e);
            throw new BusinessException(
                    FileServiceErrorCodes.FILE_UPLOAD_FAILED,
                    String.format(FileServiceErrorMessages.FILE_UPLOAD_FAILED, e.getMessage())
            );
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

        log.info("File instant upload (deduplication): fileHash={}, userId={}, originalFilename={}",
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
                                         byte[] fileData, String fileHash, String targetBucketName) {
        String filePath = uploadObjectFactory.generateStoragePath(appId, userId, "files", file.getOriginalFilename());
        String fileUrl = uploadStorageService.uploadFile(fileData, filePath, file.getContentType());

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
            uploadStorageService.cleanupUploadedPaths(List.of(filePath));
            throw dbEx;
        }

        log.info("File uploaded successfully: filePath={}, userId={}, originalFilename={}",
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
