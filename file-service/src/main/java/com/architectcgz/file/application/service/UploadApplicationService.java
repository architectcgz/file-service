package com.architectcgz.file.application.service;

import com.github.f4b6a3.uuid.UuidCreator;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.AccessDeniedException;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.common.exception.FileNotFoundException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.model.ImageProcessConfig;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.model.UploadFile;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.domain.repository.TenantUsageRepository;
import com.architectcgz.file.domain.service.TenantDomainService;
import com.architectcgz.file.infrastructure.cache.FileUrlCacheManager;
import com.architectcgz.file.infrastructure.config.ImageProcessingProperties;
import com.architectcgz.file.infrastructure.image.ImageProcessor;
import com.architectcgz.file.infrastructure.storage.StorageService;
import com.architectcgz.file.interfaces.dto.UploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 上传应用服务
 * 
 * 使用 @ConfigurationProperties 支持配置动态刷新
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadApplicationService {
    
    private final StorageService storageService;
    private final ImageProcessor imageProcessor;
    private final StorageObjectRepository storageObjectRepository;
    private final FileRecordRepository fileRecordRepository;
    private final FileTypeValidator fileTypeValidator;
    private final TenantDomainService tenantDomainService;
    private final TenantUsageRepository tenantUsageRepository;
    private final ImageProcessingProperties imageProperties;
    private final FileUrlCacheManager fileUrlCacheManager;
    private final FileDeleteTransactionHelper deleteTransactionHelper;
    private final UploadTransactionHelper uploadTransactionHelper;
    
    /**
     * 上传图片
     *
     * 整体流程：校验 -> 图片处理 -> S3 上传 -> 短事务写库 -> 失败补偿
     * 移除了方法级 @Transactional，避免长事务持有数据库连接期间执行大量 I/O 操作。
     * 数据库写入由 UploadTransactionHelper 封装为独立短事务；
     * 若写库失败，则补偿清理已上传的 S3 文件。
     *
     * @param appId  应用ID
     * @param file   图片文件
     * @param userId 上传者ID
     * @return 上传结果
     */
    public UploadResult uploadImage(String appId, MultipartFile file, String userId) {
        // 临时文件路径，用于 finally 块清理
        Path tempSourceFile = null;
        Path tempProcessedFile = null;
        Path tempThumbnailFile = null;

        // 记录本次上传到 S3 的路径，用于写库失败时的补偿清理
        List<String> uploadedS3Paths = new ArrayList<>();

        try {
            // 0. 检查租户配额
            tenantDomainService.checkQuota(appId, file.getSize());

            // 1. 将上传文件写入临时文件，避免 file.getBytes() 占用大量堆内存
            String prefix = imageProperties.getTempFilePrefix();
            tempSourceFile = Files.createTempFile(prefix, ".tmp");
            file.transferTo(tempSourceFile.toFile());

            // 2. 读取文件头用于魔数校验（仅读取前 12 字节，不加载整个文件）
            byte[] fileHeader = readFileHeader(tempSourceFile, 12);

            fileTypeValidator.validateFileWithMagicNumber(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    fileHeader,
                    file.getSize()
            );

            // 3. 基于文件的图片处理（压缩、转 WebP），不在内存中持有完整图片 byte[]
            ImageProcessConfig config = ImageProcessConfig.builder()
                    .maxWidth(imageProperties.getMaxWidth())
                    .maxHeight(imageProperties.getMaxHeight())
                    .quality(imageProperties.getQuality())
                    .convertToWebP(imageProperties.isConvertToWebp())
                    .thumbnailWidth(imageProperties.getThumbnailWidth())
                    .thumbnailHeight(imageProperties.getThumbnailHeight())
                    .build();

            String outputExt = imageProperties.isConvertToWebp() ? ".webp" : ".jpg";
            tempProcessedFile = Files.createTempFile(prefix + "proc-", outputExt);
            long processedSize = imageProcessor.processToFile(tempSourceFile, tempProcessedFile, config);

            tempThumbnailFile = Files.createTempFile(prefix + "thumb-", ".jpg");
            imageProcessor.generateThumbnailToFile(
                    tempSourceFile, tempThumbnailFile,
                    imageProperties.getThumbnailWidth(),
                    imageProperties.getThumbnailHeight(),
                    imageProperties.getThumbnailQuality()
            );

            // 4. 基于文件流式计算哈希，避免将处理后图片加载到内存
            String fileHash = calculateFileHashFromFile(tempProcessedFile);
            String contentType = imageProperties.isConvertToWebp() ? "image/webp" : file.getContentType();

            // 5. 检查是否存在相同哈希的文件（秒传去重）
            Optional<StorageObject> existingStorageObject = storageObjectRepository.findByFileHash(appId, fileHash);

            String storageObjectId;
            String imageUrl;
            String thumbnailUrl;
            String storagePath;

            if (existingStorageObject.isPresent()) {
                // 秒传路径：主图已存在，仅上传缩略图
                StorageObject existing = existingStorageObject.get();
                storageObjectId = existing.getId();
                storagePath = existing.getStoragePath();
                imageUrl = storageService.getUrl(storagePath);

                // 缩略图仍需上传（每个用户可能有不同的缩略图需求）
                String thumbnailPath = generateStoragePath(appId, userId, "thumbnails", "jpg");
                thumbnailUrl = storageService.uploadFromFile(tempThumbnailFile, thumbnailPath, "image/jpeg");
                uploadedS3Paths.add(thumbnailPath);

                log.info("Image instant upload (deduplication): fileHash={}, userId={}, originalFilename={}",
                        fileHash, userId, file.getOriginalFilename());

                // 短事务写库：增加引用计数 + 保存 FileRecord + 更新租户用量
                String fileRecordId = generateFileId();
                FileRecord fileRecord = buildFileRecord(fileRecordId, appId, userId, storageObjectId,
                        storagePath, file.getOriginalFilename(), processedSize, contentType, fileHash);
                try {
                    uploadTransactionHelper.saveInstantUpload(storageObjectId, fileRecord, processedSize);
                } catch (Exception dbEx) {
                    // 写库失败，补偿清理已上传的缩略图
                    compensateS3Uploads(uploadedS3Paths);
                    throw dbEx;
                }

                return buildUploadResult(fileRecordId, imageUrl, thumbnailUrl,
                        file.getOriginalFilename(), processedSize, contentType);

            } else {
                // 新文件路径：上传主图和缩略图
                String extension = imageProperties.isConvertToWebp() ? "webp" : getExtensiExcepException(file.getOriginalFilename());
                String imagePath = generateStoragePath(appId, userId, "images", extension);
                String thumbnailPath = generateStoragePath(appId, userId, "thumbnails", "jpg");

                imageUrl = storageService.uploadFromFile(tempProcessedFile, imagePath, contentType);
                uploadedS3Paths.add(imagePath);

                thumbnailUrl = storageService.uploadFromFile(tempThumbnailFile, thumbnailPath, "image/jpeg");
                uploadedS3Paths.add(thumbnailPath);

                log.info("Image uploaded to S3: imagePath={}, userId={}, originalFilename={}",
                        imagePath, userId, file.getOriginalFilename());

                // 构建 StorageObject 和 FileRecord
                String storageObjectNewId = generateFileId();
                StorageObject storageObject = StorageObject.builder()
                        .id(storageObjectNewId)
                        .appId(appId)
                        .fileHash(fileHash)
                        .hashAlgorithm("MD5")
                        .storagePath(imagePath)
                        .fileSize(processedSize)
                        .contentType(contentType)
                        .referenceCount(1)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                String fileRecordId = generateFileId();
                FileRecord fileRecord = buildFileRecord(fileRecordId, appId, userId, storageObjectNewId,
                        imagePath, file.getOriginalFilename(), processedSize, contentType, fileHash);

                // 短事务写库：保存 StorageObject + FileRecord + 更新租户用量
                // 若写库失败，补偿清理已上传的 S3 文件
                try {
                    uploadTransactionHelper.saveNewUpload(storageObject, fileRecord, processedSize);
                } catch (Exception dbEx) {
                    compensateS3Uploads(uploadedS3Paths);
                    throw dbEx;
                }

                log.info("Image upload completed: fileRecordId={}, imagePath={}", fileRecordId, imagePath);

                return buildUploadResult(fileRecordId, imageUrl, thumbnailUrl,
                        file.getOriginalFilename(), processedSize, contentType);
            }


        } catch (IOException e) {
            log.error("Failed to upload image: {}", file.getOriginalFilename(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.IMAGE_UPLOAD_FAILED, e.getMessage()));
        } finally {
            // 清理所有临时文件，无论成功或失败
            deleteTempFileQuietly(tempSourceFile);
            deleteTempFileQuietly(tempProcessedFile);
            deleteTempFileQuietly(tempThumbnailFile);
        }
    }

    /**
     * 构建 FileRecord 领域对象
     */
    private FileRecord buildFileRecord(String fileRecordId, String appId, String userId,
                                       String storageObjectId, String storagePath,
                                       String originalFilename, long fileSize,
                                       String contentType, String fileHash) {
        return FileRecord.builder()
                .id(fileRecordId)
                .appId(appId)
                .userId(userId)
                .storageObjectId(storageObjectId)
                .originalFilename(originalFilename)
                .storagePath(storagePath)
                .fileSize(fileSize)
                .contentType(contentType)
                .fileHash(fileHash)
                .hashAlgorithm("MD5")
                .accessLevel(AccessLevel.PUBLIC)
                .status(FileStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 构建上传结果 DTO
     */
    private UploadResult buildUploadResult(String fileRecordId, String imageUrl, String thumbnailUrl,
                                           String originalFilename, long processedSize, String contentType) {
        return UploadResult.builder()
                .fileId(fileRecordId)
                .url(imageUrl)
                .thumbnailUrl(thumbnailUrl)
                .originalFilename(originalFilename)
                .size(processedSize)
                .fileType(UploadFile.FileType.IMAGE.name())
                .contentType(contentType)
                .build();
    }

    /**
     * S3 补偿清理：写库失败后删除已上传的 S3 文件
     * 补偿失败仅记录警告日志，不抛出异常（避免掩盖原始异常）
     *
     * @param s3Paths 需要清理的 S3 路径列表
     */
    private void compensateS3Uploads(List<String> s3Paths) {
        for (String path : s3Paths) {
            try {
                storageService.delete(path);
                log.warn("S3 compensation cleanup: deleted path={}", path);
            } catch (Exception cleanupEx) {
                log.error("S3 compensation cleanup failed: path={}", path, cleanupEx);
            }
        }
    }
    
    /**
     * 上传文件
     *
     * @param appId 应用ID
     * @param file 文件
     * @param userId 上传者ID
     * @return 上传结果
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadResult uploadFile(String appId, MultipartFile file, String userId) {
        try {
            // 0. 检查租户配额
            tenantDomainService.checkQuota(appId, file.getSize());
            
            // 1. 读取文件内容
            byte[] fileData = file.getBytes();
            
            // 2. 验证文件（使用FileTypeValidator)
            byte[] fileHeader = new byte[Math.min(12, fileData.length)];
            System.arraycopy(fileData, 0, fileHeader, 0, fileHeader.length);
            
            fileTypeValidator.validateFileWithMagicNumber(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    fileHeader,
                    file.getSize()
            );
            
            // 3. 计算文件哈希
            String fileHash = calculateFileHash(fileData);
            
            // 4. 检查是否存在相同哈希的文件（秒传去重)
            Optional<StorageObject> existingStorageObject = storageObjectRepository.findByFileHash(appId, fileHash);
            
            String storageObjectId;
            String fileUrl;
            
            if (existingStorageObject.isPresent()) {
                // 文件已存在，增加引用计数（秒传）
                StorageObject storageObject = existingStorageObject.get();
                storageObjectRepository.incrementReferenceCount(storageObject.getId());
                storageObjectId = storageObject.getId();
                fileUrl = storageService.getUrl(storageObject.getStoragePath());
                
                log.info("File instant upload (deduplication): fileHash={}, userId={}, originalFilename={}", 
                        fileHash, userId, file.getOriginalFilename());
            } else {
                // 新文件，上传到存储并创建 StorageObject
                String extension = getExtensiExcepException(file.getOriginalFilename());
                String filePath = generateStoragePath(appId, userId, "files", extension);
                
                fileUrl = storageService.upload(fileData, filePath, file.getContentType());
                
                // 创建 StorageObject
                StorageObject storageObject = StorageObject.builder()
                        .id(generateFileId())
                        .appId(appId)
                        .fileHash(fileHash)
                        .hashAlgorithm("MD5")
                        .storagePath(filePath)
                        .fileSize(file.getSize())
                        .contentType(file.getContentType())
                        .referenceCount(1)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                
                storageObjectRepository.save(storageObject);
                storageObjectId = storageObject.getId();
                
                log.info("File uploaded successfully: filePath={}, userId={}, originalFilename={}", 
                        filePath, userId, file.getOriginalFilename());
            }
            
            // 5. 创建 FileRecord
            String fileRecordId = generateFileId();
            
            // 从 StorageObject 获取 storagePath
            StorageObject storageObject = storageObjectRepository.findById(storageObjectId)
                    .orElseThrow(() -> new BusinessException(FileServiceErrorMessages.STORAGE_OBJECT_NOT_FOUND));

            FileRecord fileRecord = FileRecord.builder()
                    .id(fileRecordId)
                    .appId(appId)
                    .userId(userId)
                    .storageObjectId(storageObjectId)
                    .originalFilename(file.getOriginalFilename())
                    .storagePath(storageObject.getStoragePath())
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .fileHash(fileHash)
                    .hashAlgorithm("MD5")
                    .accessLevel(AccessLevel.PUBLIC)
                    .status(FileStatus.COMPLETED)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            fileRecordRepository.save(fileRecord);
            
            // 6. 更新租户使用统计（原子操作）
            tenantUsageRepository.incrementUsage(appId, file.getSize());
            
            return UploadResult.builder()
                    .fileId(fileRecordId)
                    .url(fileUrl)
                    .originalFilename(file.getOriginalFilename())
                    .size(file.getSize())
                    .fileType(UploadFile.FileType.DOCUMENT.name())
                    .contentType(file.getContentType())
                    .build();
                    
        } catch (IOException e) {
            log.error("Failed to upload file: {}", file.getOriginalFilename(), e);
            throw new BusinessException(String.format(FileServiceErrorMessages.FILE_UPLOAD_FAILED, e.getMessage()));
        }
    }
    
    /**
     * 删除文件
     * 操作顺序：查询校验 -> 事务外判断是否需要删 S3 -> 先删 S3 -> 短事务更新数据库
     *
     * 为什么先删 S3 再更新数据库：
     * - 若先删库再删 S3，S3 删除失败时 StorageObject 记录已丢失，孤立文件无法被定时任务追踪；
     * - 先删 S3 再删库，S3 删除失败时整个操作中止，数据库保持完整，可重试或由定时任务兜底。
     *
     * @param appId        应用ID
     * @param fileRecordId 文件记录ID
     * @param userId       用户ID（用于权限验证）
     */
    public void deleteFile(String appId, String fileRecordId, String userId) {
        // 1. 查找文件记录
        FileRecord fileRecord = fileRecordRepository.findById(fileRecordId)
                .orElseThrow(() -> FileNotFoundException.notFound(fileRecordId));

        // 2. 验证 appId 归属
        if (!fileRecord.belongsToApp(appId)) {
            throw new AccessDeniedException(FileServiceErrorMessages.FILE_NOT_BELONG_TO_APP);
        }

        // 3. 验证用户权限
        if (!fileRecord.getUserId().equals(userId)) {
            throw new AccessDeniedException(FileServiceErrorMessages.ACCESS_DENIED_DELETE_FILE);
        }

        // 4. 事务外预判：查询 StorageObject，判断引用计数是否为最后一个（递减后归零需删 S3）
        String storageObjectId = fileRecord.getStorageObjectId();
        boolean needDeleteS3 = deleteTransactionHelper
                .findStorageObjectIfLastReference(storageObjectId)
                .isPresent();

        // 5. 如果引用计数将归零，先执行 S3 删除；S3 删除失败则整个操作中止，数据库保持不变
        if (needDeleteS3) {
            String storagePath = storageObjectRepository.findById(storageObjectId)
                    .map(StorageObject::getStoragePath)
                    .orElse(null);
            if (storagePath != null) {
                log.info("引用计数将归零，先删除 S3 对象: storageObjectId={}, path={}",
                        storageObjectId, storagePath);
                storageService.delete(storagePath);
                log.info("S3 对象删除成功: path={}", storagePath);
            }
        }


        // 6. S3 删除成功后，短事务内原子更新数据库（软删 FileRecord、递减引用计数、删 StorageObject 记录）
        deleteTransactionHelper.commitUserDelete(
                appId, fileRecordId, storageObjectId, fileRecord.getFileSize());

        // 7. 清除文件 URL 缓存，避免已删除文件的 URL 继续命中缓存
        fileUrlCacheManager.evict(fileRecordId);

        log.info("文件已删除: fileRecordId={}, userId={}, s3Deleted={}", fileRecordId, userId, needDeleteS3);
    }
    
    /**
     * 生成文件ID
     */
    /**
     * 生成文件ID (使用UUIDv7 - 时间有序的UUID)
     * UUIDv7 包含时间戳前缀，天然支持按时间排序，适合分布式环境
     */
    private String generateFileId() {
        return UuidCreator.getTimeOrderedEpoch().toString();
    }
    
    /**
     * 获取文件扩展名
     */
    private String getExtensiExcepException(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
    
    /**
     * 生成存储路径（日期分文件夹）
     * 格式: {year}/{month}/{day}/{userId}/{type}/{uuid}.{ext}
     * 
     * @param userId 用户ID
     * @param type 文件类型（images/files/thumbnails)
     * @param extension 文件扩展名
     * @return 存储路径
     */
    private String generateStoragePath(String appId, String userId, String type, String extension) {
        LocalDateTime now = LocalDateTime.now();
        String fileId = generateFileId();
        
        return String.format("%s/%d/%02d/%02d/%s/%s/%s.%s",
                appId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                userId,
                type,
                fileId,
                extension);
    }
    
    /**
     * 计算文件哈希值（MD5或
     *
     * @param data 文件数据
     * @return MD5 哈希值（十六进制字符串）
     */
    private String calculateFileHash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(data);

            // 转换为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 algorithm not available", e);
            throw new BusinessException(FileServiceErrorMessages.FILE_HASH_FAILED);
        }
    }

    /**
     * 基于文件流式计算 MD5 哈希，避免将整个文件加载到内存
     * 使用 DigestInputStream 边读边算，内存占用仅为缓冲区大小（8KB）
     *
     * @param file 文件路径
     * @return MD5 哈希值（十六进制字符串）
     */
    private String calculateFileHashFromFile(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // 边读边计算哈希，不需要处理读取的数据
                }
            }
            byte[] hashBytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 algorithm not available", e);
            throw new BusinessException(FileServiceErrorMessages.FILE_HASH_FAILED);
        } catch (IOException e) {
            log.error("Failed to calculate file hash: {}", file, e);
            throw new BusinessException(FileServiceErrorMessages.FILE_HASH_FAILED + ": " + e.getMessage());
        }
    }

    /**
     * 读取文件头部指定字节数，用于魔数校验
     * 仅读取前 N 字节，不加载整个文件到内存
     *
     * @param file 文件路径
     * @param length 需要读取的字节数
     * @return 文件头部字节数组
     */
    private byte[] readFileHeader(Path file, int length) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            long fileSize = Files.size(file);
            int readLength = (int) Math.min(length, fileSize);
            byte[] header = new byte[readLength];
            int bytesRead = is.read(header);
            if (bytesRead < readLength) {
                byte[] actual = new byte[bytesRead];
                System.arraycopy(header, 0, actual, 0, bytesRead);
                return actual;
            }
            return header;
        }
    }

    /**
     * 静默删除临时文件，忽略删除失败的异常
     * 用于 finally 块中清理临时文件
     *
     * @param tempFile 临时文件路径，允许为 null
     */
    private void deleteTempFileQuietly(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("Failed to delete temp file: {}", tempFile, e);
            }
        }
    }
}
