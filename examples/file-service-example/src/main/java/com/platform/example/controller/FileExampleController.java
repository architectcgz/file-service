package com.platform.example.controller;

import com.platform.example.service.FileAccessExampleService;
import com.platform.example.service.FileUploadExampleService;
import com.platform.fileservice.client.model.FileDetailResponse;
import com.platform.fileservice.client.model.FileUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 文件操作示例控制器
 * 
 * 提供 REST API 演示文件上传、下载和管理功能
 */
@Slf4j
@RestController
@RequestMapping("/api/examples")
@RequiredArgsConstructor
public class FileExampleController {

    private final FileUploadExampleService uploadService;
    private final FileAccessExampleService accessService;

    /**
     * 上传图片示例
     */
    @PostMapping("/upload-image")
    public ResponseEntity<FileUploadResponse> uploadImage(
            @RequestParam("file") MultipartFile file) {
        
        try {
            // 将 MultipartFile 转换为临时文件
            File tempFile = convertToFile(file);
            
            // 上传图片
            FileUploadResponse response = uploadService.uploadImageExample(tempFile);
            
            // 清理临时文件
            tempFile.delete();
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("文件处理失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 上传私有文件示例
     */
    @PostMapping("/upload-private")
    public ResponseEntity<FileUploadResponse> uploadPrivateFile(
            @RequestParam("file") MultipartFile file) {
        
        try {
            File tempFile = convertToFile(file);
            FileUploadResponse response = uploadService.uploadPrivateFileExample(tempFile);
            tempFile.delete();
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("文件处理失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 秒传示例
     */
    @PostMapping("/instant-upload")
    public ResponseEntity<FileUploadResponse> instantUpload(
            @RequestParam("file") MultipartFile file) {
        
        try {
            File tempFile = convertToFile(file);
            FileUploadResponse response = uploadService.instantUploadExample(tempFile);
            tempFile.delete();
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("文件处理失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 大文件分片上传示例
     */
    @PostMapping("/multipart-upload")
    public ResponseEntity<FileUploadResponse> multipartUpload(
            @RequestParam("file") MultipartFile file) {
        
        try {
            File tempFile = convertToFile(file);
            FileUploadResponse response = uploadService.multipartUploadExample(tempFile);
            tempFile.delete();
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("文件处理失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取文件 URL 示例
     */
    @GetMapping("/file-url/{fileId}")
    public ResponseEntity<String> getFileUrl(@PathVariable String fileId) {
        String url = accessService.getFileUrlExample(fileId);
        return ResponseEntity.ok(url);
    }

    /**
     * 获取文件详情示例
     */
    @GetMapping("/file-detail/{fileId}")
    public ResponseEntity<FileDetailResponse> getFileDetail(@PathVariable String fileId) {
        FileDetailResponse detail = accessService.getFileDetailExample(fileId);
        return ResponseEntity.ok(detail);
    }

    /**
     * 删除文件示例
     */
    @DeleteMapping("/file/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable String fileId) {
        accessService.deleteFileExample(fileId);
        return ResponseEntity.ok().build();
    }

    /**
     * 将 MultipartFile 转换为临时文件
     */
    private File convertToFile(MultipartFile multipartFile) throws IOException {
        Path tempFile = Files.createTempFile("upload-", "-" + multipartFile.getOriginalFilename());
        Files.copy(multipartFile.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        return tempFile.toFile();
    }
}
