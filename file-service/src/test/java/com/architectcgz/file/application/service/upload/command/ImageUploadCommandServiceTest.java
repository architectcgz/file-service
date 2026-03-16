package com.architectcgz.file.application.service.upload.command;

import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.UploadTransactionHelper;
import com.architectcgz.file.application.service.upload.UploadDedupCoordinatorService;
import com.architectcgz.file.application.service.upload.factory.UploadObjectFactory;
import com.architectcgz.file.application.service.upload.file.PreparedUploadSource;
import com.architectcgz.file.application.service.upload.file.UploadFileHashService;
import com.architectcgz.file.application.service.upload.file.UploadTempFileService;
import com.architectcgz.file.application.service.upload.image.UploadImageFormatResolver;
import com.architectcgz.file.application.service.upload.storage.UploadStorageService;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.model.UploadFile;
import com.architectcgz.file.domain.service.TenantDomainService;
import com.architectcgz.file.infrastructure.config.ImageProcessingProperties;
import com.architectcgz.file.infrastructure.image.ImageProcessor;
import com.architectcgz.file.interfaces.dto.UploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageUploadCommandService 单元测试")
class ImageUploadCommandServiceTest {

    @Mock
    private UploadObjectFactory uploadObjectFactory;
    @Mock
    private UploadStorageService uploadStorageService;
    @Mock
    private UploadDedupCoordinatorService uploadDedupCoordinatorService;
    @Mock
    private UploadFileHashService uploadFileHashService;
    @Mock
    private UploadTempFileService uploadTempFileService;
    @Mock
    private UploadImageFormatResolver uploadImageFormatResolver;
    @Mock
    private ImageProcessor imageProcessor;
    @Mock
    private FileTypeValidator fileTypeValidator;
    @Mock
    private TenantDomainService tenantDomainService;
    @Mock
    private UploadTransactionHelper uploadTransactionHelper;
    @Mock
    private MultipartFile multipartFile;

    private ImageUploadCommandService imageUploadCommandService;

    @BeforeEach
    void setUp() {
        ImageProcessingProperties imageProcessingProperties = new ImageProcessingProperties();
        imageProcessingProperties.setTempFilePrefix("img-test-");
        imageProcessingProperties.setConvertToWebp(true);
        imageProcessingProperties.setMaxWidth(1920);
        imageProcessingProperties.setMaxHeight(1080);
        imageProcessingProperties.setQuality(0.85);
        imageProcessingProperties.setThumbnailWidth(200);
        imageProcessingProperties.setThumbnailHeight(200);
        imageProcessingProperties.setThumbnailQuality(0.8);

        imageUploadCommandService = new ImageUploadCommandService(
                uploadObjectFactory,
                uploadStorageService,
                uploadDedupCoordinatorService,
                uploadFileHashService,
                uploadTempFileService,
                uploadImageFormatResolver,
                imageProcessingProperties,
                imageProcessor,
                fileTypeValidator,
                tenantDomainService,
                uploadTransactionHelper
        );
    }

    @Test
    @DisplayName("图片上传应走临时文件和去重锁链路")
    void uploadImage_shouldUseTempFileFlowAndDedupLock() throws Exception {
        byte[] fileContent = "fake-jpeg".getBytes();
        StorageObject storageObject = StorageObject.builder()
                .id("storage-001")
                .bucketName("platform-files-public")
                .storagePath("blog/images/object.webp")
                .build();
        FileRecord fileRecord = FileRecord.builder()
                .id("file-001")
                .appId("blog")
                .storageObjectId("storage-001")
                .storagePath("blog/images/object.webp")
                .build();
        UploadResult expected = UploadResult.builder()
                .fileId("file-001")
                .url("http://localhost:9000/platform-files-public/blog/images/object.webp")
                .thumbnailUrl("http://localhost:9000/platform-files-public/blog/thumbnails/object.jpg")
                .originalFilename("avatar.jpg")
                .size(256L)
                .fileType(UploadFile.FileType.IMAGE.name())
                .contentType("image/webp")
                .build();

        when(multipartFile.getOriginalFilename()).thenReturn("avatar.jpg");
        when(multipartFile.getContentType()).thenReturn("image/jpeg");
        when(multipartFile.getSize()).thenReturn((long) fileContent.length);
        Path tempSourceFile = Files.createTempFile("img-source-test-", ".tmp");
        Files.write(tempSourceFile, fileContent);
        when(uploadTempFileService.prepareMultipartFile(eq(multipartFile), eq("img-test-"), eq(".tmp"), eq(12), eq(false)))
                .thenReturn(new PreparedUploadSource(tempSourceFile, fileContent, null));
        when(uploadImageFormatResolver.resolveProcessedExtension(multipartFile)).thenReturn("webp");
        when(uploadImageFormatResolver.resolveProcessedContentType(multipartFile, "webp")).thenReturn("image/webp");
        when(imageProcessor.processToFile(any(Path.class), any(Path.class), any())).thenReturn(256L);
        when(imageProcessor.generateThumbnailToFile(any(Path.class), any(Path.class), anyInt(), anyInt(), anyDouble()))
                .thenReturn(96L);
        when(uploadFileHashService.calculateHash(any(Path.class))).thenReturn("hash-001");
        when(uploadStorageService.resolveUploadBucketName()).thenReturn("platform-files-public");
        when(uploadDedupCoordinatorService.executeWithDedupClaim(eq("blog"), eq("hash-001"), eq("platform-files-public"), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<UploadResult> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
        when(uploadObjectFactory.generateStoragePathWithExtension("blog", "user-001", "images", "webp"))
                .thenReturn("blog/images/object.webp");
        when(uploadObjectFactory.generateStoragePathWithExtension("blog", "user-001", "thumbnails", "jpg"))
                .thenReturn("blog/thumbnails/object.jpg");
        when(uploadStorageService.uploadTempFile(any(Path.class), eq("blog/images/object.webp"), eq("image/webp")))
                .thenReturn("http://localhost:9000/platform-files-public/blog/images/object.webp");
        when(uploadStorageService.uploadTempFile(any(Path.class), eq("blog/thumbnails/object.jpg"), eq("image/jpeg")))
                .thenReturn("http://localhost:9000/platform-files-public/blog/thumbnails/object.jpg");
        when(uploadObjectFactory.generateFileId()).thenReturn("storage-001", "file-001");
        when(uploadObjectFactory.buildStorageObject(
                "blog", "storage-001", "hash-001", "blog/images/object.webp",
                256L, "image/webp", "platform-files-public"
        )).thenReturn(storageObject);
        when(uploadObjectFactory.buildFileRecord(
                "file-001", "blog", "user-001", "storage-001", "blog/images/object.webp",
                "avatar.jpg", 256L, "image/webp", "hash-001"
        )).thenReturn(fileRecord);
        when(uploadObjectFactory.buildUploadResult(
                "file-001",
                "http://localhost:9000/platform-files-public/blog/images/object.webp",
                "http://localhost:9000/platform-files-public/blog/thumbnails/object.jpg",
                "avatar.jpg",
                256L,
                UploadFile.FileType.IMAGE,
                "image/webp"
        )).thenReturn(expected);

        UploadResult actual = imageUploadCommandService.uploadImage("blog", multipartFile, "user-001");

        assertThat(actual).isSameAs(expected);
        verify(tenantDomainService).validateUploadPrerequisites("blog", fileContent.length);
        verify(uploadDedupCoordinatorService).executeWithDedupClaim(eq("blog"), eq("hash-001"), eq("platform-files-public"), any(), any());
        verify(uploadTempFileService).prepareMultipartFile(eq(multipartFile), eq("img-test-"), eq(".tmp"), eq(12), eq(false));
        verify(uploadFileHashService).calculateHash(any(Path.class));
        verify(uploadStorageService).uploadTempFile(any(Path.class), eq("blog/images/object.webp"), eq("image/webp"));
        verify(uploadStorageService).uploadTempFile(any(Path.class), eq("blog/thumbnails/object.jpg"), eq("image/jpeg"));
        verify(uploadTransactionHelper).saveNewUpload(storageObject, fileRecord, 256L);
        verify(uploadTempFileService, times(3)).deleteQuietly(any(Path.class));
        verify(multipartFile, never()).getBytes();
    }
}
