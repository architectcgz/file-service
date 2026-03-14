package com.architectcgz.file.application.service.upload.command;

import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.UploadTransactionHelper;
import com.architectcgz.file.application.service.upload.factory.UploadObjectFactory;
import com.architectcgz.file.application.service.upload.file.UploadFileHashService;
import com.architectcgz.file.application.service.upload.file.UploadTempFileService;
import com.architectcgz.file.application.service.upload.image.UploadImageFormatResolver;
import com.architectcgz.file.application.service.upload.storage.UploadStorageService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.ImageProcessConfig;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.model.UploadFile;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.service.TenantDomainService;
import com.architectcgz.file.infrastructure.config.ImageProcessingProperties;
import com.architectcgz.file.infrastructure.image.ImageProcessor;
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
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUploadCommandService {

    private final UploadObjectFactory uploadObjectFactory;
    private final UploadStorageService uploadStorageService;
    private final UploadFileHashService uploadFileHashService;
    private final UploadTempFileService uploadTempFileService;
    private final UploadImageFormatResolver uploadImageFormatResolver;
    private final ImageProcessingProperties imageProcessingProperties;
    private final ImageProcessor imageProcessor;
    private final StorageObjectRepository storageObjectRepository;
    private final FileTypeValidator fileTypeValidator;
    private final TenantDomainService tenantDomainService;
    private final UploadTransactionHelper uploadTransactionHelper;

    public UploadResult uploadImage(String appId, MultipartFile file, String userId) {
        Path tempSourceFile = null;
        Path tempProcessedFile = null;
        Path tempThumbnailFile = null;
        List<String> uploadedStoragePaths = new ArrayList<>();

        try {
            tenantDomainService.checkQuota(appId, file.getSize());

            String prefix = imageProcessingProperties.getTempFilePrefix();
            tempSourceFile = Files.createTempFile(prefix, ".tmp");
            file.transferTo(tempSourceFile.toFile());

            byte[] fileHeader = uploadTempFileService.readHeader(tempSourceFile, 12);
            fileTypeValidator.validateFileWithMagicNumber(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    fileHeader,
                    file.getSize()
            );

            ImageProcessConfig config = ImageProcessConfig.builder()
                    .maxWidth(imageProcessingProperties.getMaxWidth())
                    .maxHeight(imageProcessingProperties.getMaxHeight())
                    .quality(imageProcessingProperties.getQuality())
                    .convertToWebP(imageProcessingProperties.isConvertToWebp())
                    .thumbnailWidth(imageProcessingProperties.getThumbnailWidth())
                    .thumbnailHeight(imageProcessingProperties.getThumbnailHeight())
                    .build();

            String processedImageExtension = uploadImageFormatResolver.resolveProcessedExtension(file);
            String processedImageContentType = uploadImageFormatResolver
                    .resolveProcessedContentType(file, processedImageExtension);

            tempProcessedFile = Files.createTempFile(prefix + "proc-", "." + processedImageExtension);
            long processedSize = imageProcessor.processToFile(tempSourceFile, tempProcessedFile, config);

            tempThumbnailFile = Files.createTempFile(prefix + "thumb-", ".jpg");
            imageProcessor.generateThumbnailToFile(
                    tempSourceFile,
                    tempThumbnailFile,
                    imageProcessingProperties.getThumbnailWidth(),
                    imageProcessingProperties.getThumbnailHeight(),
                    imageProcessingProperties.getThumbnailQuality()
            );

            String fileHash = uploadFileHashService.calculateHash(tempProcessedFile);
            String targetBucketName = uploadStorageService.resolveUploadBucketName();

            Optional<StorageObject> existingStorageObject = storageObjectRepository.findByFileHashAndBucket(
                    appId, fileHash, targetBucketName
            );

            if (existingStorageObject.isPresent()) {
                return handleInstantImageUpload(
                        appId, userId, file, processedSize, processedImageContentType, fileHash,
                        tempThumbnailFile, uploadedStoragePaths, existingStorageObject.get()
                );
            }

            return handleNewImageUpload(
                    appId, userId, file, processedSize, processedImageExtension, processedImageContentType,
                    fileHash, tempProcessedFile, tempThumbnailFile, uploadedStoragePaths, targetBucketName
            );
        } catch (IOException e) {
            log.error("Failed to upload image: {}", file.getOriginalFilename(), e);
            throw new BusinessException(
                    FileServiceErrorCodes.IMAGE_UPLOAD_FAILED,
                    String.format(FileServiceErrorMessages.IMAGE_UPLOAD_FAILED, e.getMessage())
            );
        } finally {
            uploadTempFileService.deleteQuietly(tempSourceFile);
            uploadTempFileService.deleteQuietly(tempProcessedFile);
            uploadTempFileService.deleteQuietly(tempThumbnailFile);
        }
    }

    private UploadResult handleInstantImageUpload(String appId, String userId, MultipartFile file,
                                                  long processedSize, String contentType, String fileHash,
                                                  Path tempThumbnailFile, List<String> uploadedStoragePaths,
                                                  StorageObject existingStorageObject) {
        String thumbnailPath = uploadObjectFactory.generateStoragePathWithExtension(appId, userId, "thumbnails", "jpg");
        String thumbnailUrl = uploadStorageService.uploadTempFile(tempThumbnailFile, thumbnailPath, "image/jpeg");
        uploadedStoragePaths.add(thumbnailPath);

        log.info("Image instant upload (deduplication): fileHash={}, userId={}, originalFilename={}",
                fileHash, userId, file.getOriginalFilename());

        String fileRecordId = uploadObjectFactory.generateFileId();
        var fileRecord = uploadObjectFactory.buildFileRecord(
                fileRecordId,
                appId,
                userId,
                existingStorageObject.getId(),
                existingStorageObject.getStoragePath(),
                file.getOriginalFilename(),
                processedSize,
                contentType,
                fileHash
        );

        try {
            uploadTransactionHelper.saveInstantUpload(existingStorageObject.getId(), fileRecord, processedSize);
        } catch (Exception dbEx) {
            uploadStorageService.cleanupUploadedPaths(uploadedStoragePaths);
            throw dbEx;
        }

        String imageUrl = uploadStorageService.resolvePublicUrl(
                existingStorageObject.getBucketName(),
                existingStorageObject.getStoragePath()
        );

        return uploadObjectFactory.buildUploadResult(
                fileRecordId,
                imageUrl,
                thumbnailUrl,
                file.getOriginalFilename(),
                processedSize,
                UploadFile.FileType.IMAGE,
                contentType
        );
    }

    private UploadResult handleNewImageUpload(String appId, String userId, MultipartFile file,
                                              long processedSize, String extension, String contentType,
                                              String fileHash, Path tempProcessedFile, Path tempThumbnailFile,
                                              List<String> uploadedStoragePaths, String targetBucketName) {
        String imagePath = uploadObjectFactory.generateStoragePathWithExtension(appId, userId, "images", extension);
        String thumbnailPath = uploadObjectFactory.generateStoragePathWithExtension(appId, userId, "thumbnails", "jpg");

        String imageUrl = uploadStorageService.uploadTempFile(tempProcessedFile, imagePath, contentType);
        uploadedStoragePaths.add(imagePath);

        String thumbnailUrl = uploadStorageService.uploadTempFile(tempThumbnailFile, thumbnailPath, "image/jpeg");
        uploadedStoragePaths.add(thumbnailPath);

        log.info("Image uploaded to storage: imagePath={}, userId={}, originalFilename={}",
                imagePath, userId, file.getOriginalFilename());

        String storageObjectId = uploadObjectFactory.generateFileId();
        var storageObject = uploadObjectFactory.buildStorageObject(
                appId, storageObjectId, fileHash, imagePath, processedSize, contentType, targetBucketName
        );

        String fileRecordId = uploadObjectFactory.generateFileId();
        var fileRecord = uploadObjectFactory.buildFileRecord(
                fileRecordId, appId, userId, storageObjectId, imagePath,
                file.getOriginalFilename(), processedSize, contentType, fileHash
        );

        try {
            uploadTransactionHelper.saveNewUpload(storageObject, fileRecord, processedSize);
        } catch (Exception dbEx) {
            uploadStorageService.cleanupUploadedPaths(uploadedStoragePaths);
            throw dbEx;
        }

        log.info("Image upload completed: fileRecordId={}, imagePath={}", fileRecordId, imagePath);
        return uploadObjectFactory.buildUploadResult(
                fileRecordId,
                imageUrl,
                thumbnailUrl,
                file.getOriginalFilename(),
                processedSize,
                UploadFile.FileType.IMAGE,
                contentType
        );
    }
}
