package com.architectcgz.file.application.service;

import com.github.f4b6a3.uuid.UuidCreator;
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
    
    /**
     * 上传图片
     *
     * @param appId 应用ID
     * @param file 图片文件
     * @param userId 上传者ID
     * @return 上传结果
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadResult uploadImage(String appId, MultipartFile file, String userId) {
        // 临时文件路径，用于 finally 块清理
        Path tempSourceFile = null;
        Path tempProcessedFile = null;
        Path tempThumbnailFile = null;

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
                    imageProperties.getThumbnailHeight()
            );

            // 4. 基于文件流式计算哈希，避免将处理后图片加载到内存
            String fileHash = calculateFileHashFromFile(tempProcessedFile);
            String contentType = imageProperties.isConvertToWebp() ? "image/webp" : file.getContentType();

            // 5. 检查是否存在相同哈希的文件（秒传去重）
            Optional<StorageObject> existingStorageObject = storageObjectRepository.findByFileHash(appId, fileHash);

            String storageObjectId;
            String imageUrl;
            String thumbnailUrl;

            if (existingStorageObject.isPresent()) {
                // 文件已存在，增加引用计数（秒传）
                StorageObject storageObject = existingStorageObject.get();
                storageObjectRepository.incrementReferenceCount(storageObject.getId());
                storageObjectId = storageObject.getId();
                imageUrl = storageService.getUrl(storageObject.getStoragePath());

                // 缩略图仍需上传（因为每个用户可能有不同的缩略图需求）
                String thumbnailPath = generateStoragePath(appId, userId, "thumbnails", "jpg");
                thumbnailUrl = storageService.uploadFromFile(tempThumbnailFile, thumbnailPath, "image/jpeg");

                log.info("Image instant upload (deduplication): fileHash={}, userId={}, originalFilename={}",
                        fileHash, userId, file.getOriginalFilename());
            } else {
                // 新文件，从临时文件流式上传到存储
                String extension = imageProperties.isConvertToWebp() ? "webp" : getExtensiExcepException(file.getOriginalFilename());
                String imagePath = generateStoragePath(appId, userId, "images", extension);
                String thumbnailPath = generateStoragePath(appId, userId, "thumbnails", "jpg");

                imageUrl = storageService.uploadFromFile(tempProcessedFile, imagePath, contentType);
                thumbnailUrl = storageService.uploadFromFile(tempThumbnailFile, thumbnailPath, "image/jpeg");

                // 创建 StorageObject
                StorageObject storageObject = StorageObject.builder()
                        .id(generateFileId())
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

                storageObjectRepository.save(storageObject);
                storageObjectId = storageObject.getId();

                log.info("Image uploaded successfully: imagePath={}, userId={}, originalFilename={}",
                        imagePath, userId, file.getOriginalFilename());
            }

            // 6. 创建 FileRecord
            String fileRecordId = generateFileId();

            StorageObject storageObject = storageObjectRepository.findById(storageObjectId)
                    .orElseThrow(() -> new BusinessException("存储对象不存在"));

            FileRecord fileRecord = FileRecord.builder()
                    .id(fileRecordId)
                    .appId(appId)
                    .userId(userId)
                    .storageObjectId(storageObjectId)
                    .originalFilename(file.getOriginalFilename())
                    .storagePath(storageObject.getStoragePath())
                    .fileSize(processedSize)
                    .contentType(contentType)
                    .fileHash(fileHash)
                    .hashAlgorithm("MD5")
                    .accessLevel(AccessLevel.PUBLIC)
                    .status(FileStatus.COMPLETED)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            fileRecordRepository.save(fileRecord);

            // 7. 更新租户使用统计（原子操作）
            tenantUsageRepository.incrementUsage(appId, processedSize);

            return UploadResult.builder()
                    .fileId(fileRecordId)
                    .url(imageUrl)
                    .thumbnailUrl(thumbnailUrl)
                    .originalFilename(file.getOriginalFilename())
                    .size(processedSize)
                    .fileType(UploadFile.FileType.IMAGE.name())
                    .contentType(contentType)
                    .build();

        } catch (IOException e) {
            log.error("Failed to upload image: {}", file.getOriginalFilename(), e);
            throw new BusinessException("图片上传失败: " + e.getMessage());
        } finally {
            // 清理所有临时文件，无论成功或失败
            deleteTempFileQuietly(tempSourceFile);
            deleteTempFileQuietly(tempProcessedFile);
            deleteTempFileQuietly(tempThumbnailFile);
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
                    .orElseThrow(() -> new BusinessException("存储对象不存在"));
            
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
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除文件
     *
     * @param appId 应用ID
     * @param fileRecordId 文件记录ID
     * @param userId 用户ID（用于权限验证）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(String appId, String fileRecordId, String userId) {
        // 1. 查找文件记录
        FileRecord fileRecord = fileRecordRepository.findById(fileRecordId)
                .orElseThrow(() -> FileNotFoundException.notFound(fileRecordId));
        
        // 2. 验证 appId 归属
        if (!fileRecord.belongsToApp(appId)) {
            throw new AccessDeniedException("Access denied: file belongs to different app");
        }
        
        // 3. 验证用户权限
        if (!fileRecord.getUserId().equals(userId)) {
            throw new AccessDeniedException("无权删除该文件");
        }
        
        // 4. 更新 FileRecord 状态为 DELETED（软删除）
        fileRecordRepository.updateStatus(fileRecordId, FileStatus.DELETED);
        
        // 4.5. 更新租户使用统计（减少使用量）
        tenantUsageRepository.decrementUsage(appId, fileRecord.getFileSize());
        
        // 5. 减少 StorageObject 引用计数
        String storageObjectId = fileRecord.getStorageObjectId();
        storageObjectRepository.decrementReferenceCount(storageObjectId);
        
        // 6. 检查引用计数，如果或0 则删除S3 对象)StorageObject 记录
        Optional<StorageObject> storageObjectOpt = storageObjectRepository.findById(storageObjectId);
        if (storageObjectOpt.isPresent()) {
            StorageObject storageObject = storageObjectOpt.get();
            if (storageObject.canBeDeleted()) {
                // 删除 S3 对象
                storageService.delete(storageObject.getStoragePath());
                
                // 删除 StorageObject 记录
                storageObjectRepository.deleteById(storageObjectId);
                
                log.info("StorageObject and S3 object deleted: storageObjectId={}, path={}", 
                        storageObjectId, storageObject.getStoragePath());
            } else {
                log.info("StorageObject still referenced: storageObjectId={}, referenceCount={}", 
                        storageObjectId, storageObject.getReferenceCount());
            }
        }
        
        log.info("File deleted: fileRecordId={}, userId={}", fileRecordId, userId);
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
            throw new BusinessException("文件哈希计算失败");
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
            throw new BusinessException("文件哈希计算失败");
        } catch (IOException e) {
            log.error("Failed to calculate file hash: {}", file, e);
            throw new BusinessException("文件哈希计算失败: " + e.getMessage());
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
