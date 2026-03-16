package com.architectcgz.file.application.service.upload.command;

import com.architectcgz.file.application.service.FileTypeValidator;
import com.architectcgz.file.application.service.UploadTransactionHelper;
import com.architectcgz.file.application.service.upload.UploadDedupCoordinatorService;
import com.architectcgz.file.application.service.upload.factory.UploadObjectFactory;
import com.architectcgz.file.application.service.upload.file.PreparedUploadSource;
import com.architectcgz.file.application.service.upload.file.UploadTempFileService;
import com.architectcgz.file.application.service.upload.storage.UploadStorageService;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.model.UploadFile;
import com.architectcgz.file.domain.service.TenantDomainService;
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
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileUploadCommandService 单元测试")
class FileUploadCommandServiceTest {

    @Mock
    private UploadObjectFactory uploadObjectFactory;
    @Mock
    private UploadStorageService uploadStorageService;
    @Mock
    private UploadDedupCoordinatorService uploadDedupCoordinatorService;
    @Mock
    private UploadTempFileService uploadTempFileService;
    @Mock
    private FileTypeValidator fileTypeValidator;
    @Mock
    private TenantDomainService tenantDomainService;
    @Mock
    private UploadTransactionHelper uploadTransactionHelper;
    @Mock
    private MultipartFile multipartFile;

    private FileUploadCommandService fileUploadCommandService;

    @BeforeEach
    void setUp() {
        fileUploadCommandService = new FileUploadCommandService(
                uploadObjectFactory,
                uploadStorageService,
                uploadDedupCoordinatorService,
                uploadTempFileService,
                fileTypeValidator,
                tenantDomainService,
                uploadTransactionHelper
        );
    }

    @Test
    @DisplayName("普通文件上传应走临时文件路径而不是 getBytes")
    void uploadFile_shouldUseTempFileFlowInsteadOfGetBytes() throws Exception {
        byte[] fileContent = "%PDF-test".getBytes();
        StorageObject storageObject = StorageObject.builder()
                .id("storage-001")
                .bucketName("platform-files-public")
                .storagePath("blog/files/object.pdf")
                .build();
        FileRecord fileRecord = FileRecord.builder()
                .id("file-001")
                .appId("blog")
                .storageObjectId("storage-001")
                .storagePath("blog/files/object.pdf")
                .build();
        UploadResult expected = UploadResult.builder()
                .fileId("file-001")
                .url("http://localhost:9000/platform-files-public/blog/files/object.pdf")
                .originalFilename("report.pdf")
                .size((long) fileContent.length)
                .fileType(UploadFile.FileType.DOCUMENT.name())
                .contentType("application/pdf")
                .build();

        when(multipartFile.getOriginalFilename()).thenReturn("report.pdf");
        when(multipartFile.getContentType()).thenReturn("application/pdf");
        when(multipartFile.getSize()).thenReturn((long) fileContent.length);
        when(uploadObjectFactory.resolveExtension("report.pdf")).thenReturn("pdf");
        Path tempFile = Files.createTempFile("file-upload-test-", ".pdf");
        Files.write(tempFile, fileContent);
        when(uploadTempFileService.prepareMultipartFile(eq(multipartFile), eq("file-upload-"), eq(".pdf"), eq(12), eq(true)))
                .thenReturn(new PreparedUploadSource(tempFile, fileContent, "hash-001"));
        when(uploadStorageService.resolveUploadBucketName()).thenReturn("platform-files-public");
        when(uploadDedupCoordinatorService.executeWithDedupClaim(eq("blog"), eq("hash-001"), eq("platform-files-public"), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<UploadResult> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
        when(uploadObjectFactory.generateStoragePath("blog", "user-001", "files", "report.pdf"))
                .thenReturn("blog/files/object.pdf");
        when(uploadStorageService.uploadTempFile(any(Path.class), eq("blog/files/object.pdf"), eq("application/pdf")))
                .thenReturn("http://localhost:9000/platform-files-public/blog/files/object.pdf");
        when(uploadObjectFactory.generateFileId()).thenReturn("storage-001", "file-001");
        when(uploadObjectFactory.buildStorageObject(
                "blog", "storage-001", "hash-001", "blog/files/object.pdf",
                (long) fileContent.length, "application/pdf", "platform-files-public"
        )).thenReturn(storageObject);
        when(uploadObjectFactory.buildFileRecord(
                "file-001", "blog", "user-001", "storage-001", "blog/files/object.pdf",
                "report.pdf", (long) fileContent.length, "application/pdf", "hash-001"
        )).thenReturn(fileRecord);
        when(uploadObjectFactory.buildUploadResult(
                "file-001",
                "http://localhost:9000/platform-files-public/blog/files/object.pdf",
                null,
                "report.pdf",
                (long) fileContent.length,
                UploadFile.FileType.DOCUMENT,
                "application/pdf"
        )).thenReturn(expected);

        UploadResult actual = fileUploadCommandService.uploadFile("blog", multipartFile, "user-001");

        assertThat(actual).isSameAs(expected);
        verify(tenantDomainService).validateUploadPrerequisites("blog", fileContent.length);
        verify(uploadTempFileService).prepareMultipartFile(eq(multipartFile), eq("file-upload-"), eq(".pdf"), eq(12), eq(true));
        verify(uploadDedupCoordinatorService).executeWithDedupClaim(eq("blog"), eq("hash-001"), eq("platform-files-public"), any(), any());
        verify(uploadStorageService).uploadTempFile(any(Path.class), eq("blog/files/object.pdf"), eq("application/pdf"));
        verify(uploadTransactionHelper).saveNewUpload(storageObject, fileRecord, fileContent.length);
        verify(uploadTempFileService).deleteQuietly(any(Path.class));
        verify(multipartFile, never()).getBytes();
    }
}
