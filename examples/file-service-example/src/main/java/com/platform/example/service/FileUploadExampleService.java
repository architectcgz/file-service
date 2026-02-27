package com.platform.example.service;

import com.platform.fileservice.client.FileServiceClient;
import com.platform.fileservice.client.exception.*;
import com.platform.fileservice.client.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件上传示例服务
 * 
 * 演示各种文件上传场景的使用方法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadExampleService {

    private final FileServiceClient fileServiceClient;

    /**
     * 示例 1: 基本图片上传
     * 
     * 演示如何上传图片文件
     */
    public FileUploadResponse uploadImageExample(File imageFile) {
        try {
            log.info("开始上传图片: {}", imageFile.getName());
            
            // 直接上传图片文件
            FileUploadResponse response = fileServiceClient.uploadImage(imageFile);
            
            log.info("图片上传成功! 文件ID: {}, URL: {}", 
                    response.getFileId(), response.getUrl());
            
            return response;
            
        } catch (InvalidRequestException e) {
            log.error("无效的请求参数: {}", e.getMessage());
            throw e;
        } catch (AuthenticationException e) {
            log.error("认证失败: {}", e.getMessage());
            throw e;
        } catch (QuotaExceededException e) {
            log.error("文件大小超过限制: {}", e.getMessage());
            throw e;
        } catch (FileServiceException e) {
            log.error("文件上传失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 示例 2: 使用 InputStream 上传文件
     * 
     * 演示如何从 InputStream 上传文件
     */
    public FileUploadResponse uploadFromStreamExample(InputStream inputStream, 
                                                      String fileName, 
                                                      long fileSize) {
        try {
            log.info("开始从流上传文件: {}, 大小: {} bytes", fileName, fileSize);
            
            // 从 InputStream 上传
            FileUploadResponse response = fileServiceClient.uploadImage(
                    inputStream, fileName, fileSize);
            
            log.info("文件上传成功! 文件ID: {}", response.getFileId());
            
            return response;
            
        } catch (FileServiceException e) {
            log.error("文件上传失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 示例 3: 上传私有文件
     * 
     * 演示如何上传私有访问级别的文件
     */
    public FileUploadResponse uploadPrivateFileExample(File file) {
        try {
            log.info("开始上传私有文件: {}", file.getName());
            
            // 上传私有文件
            FileUploadResponse response = fileServiceClient.uploadFile(
                    file, AccessLevel.PRIVATE);
            
            log.info("私有文件上传成功! 文件ID: {}, 访问级别: {}", 
                    response.getFileId(), response.getAccessLevel());
            
            return response;
            
        } catch (FileServiceException e) {
            log.error("私有文件上传失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 示例 4: 秒传（即时上传）
     * 
     * 演示如何使用文件哈希检查文件是否已存在，实现秒传功能
     */
    public FileUploadResponse instantUploadExample(File file) {
        try {
            log.info("开始检查文件是否可以秒传: {}", file.getName());
            
            // 计算文件哈希
            String fileHash = calculateMD5(file);
            String contentType = Files.probeContentType(file.toPath());
            
            // 检查文件是否已存在
            InstantUploadCheckRequest checkRequest = InstantUploadCheckRequest.builder()
                    .fileHash(fileHash)
                    .fileName(file.getName())
                    .fileSize(file.length())
                    .contentType(contentType)
                    .accessLevel(AccessLevel.PUBLIC)
                    .build();
            
            InstantUploadCheckResponse checkResponse = 
                    fileServiceClient.checkInstantUpload(checkRequest);
            
            if (checkResponse.getExists()) {
                // 文件已存在，秒传成功
                log.info("文件已存在，秒传成功! 文件ID: {}, URL: {}", 
                        checkResponse.getFileId(), checkResponse.getUrl());
                
                return FileUploadResponse.builder()
                        .fileId(checkResponse.getFileId())
                        .url(checkResponse.getUrl())
                        .originalName(file.getName())
                        .fileSize(file.length())
                        .contentType(contentType)
                        .build();
            } else {
                // 文件不存在，需要正常上传
                log.info("文件不存在，开始正常上传");
                return fileServiceClient.uploadFile(file);
            }
            
        } catch (IOException e) {
            log.error("读取文件失败: {}", e.getMessage());
            throw new RuntimeException("读取文件失败", e);
        } catch (FileServiceException e) {
            log.error("秒传检查失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 示例 5: 大文件分片上传
     * 
     * 演示如何使用分片上传处理大文件
     */
    public FileUploadResponse multipartUploadExample(File file) {
        try {
            log.info("开始分片上传大文件: {}, 大小: {} MB", 
                    file.getName(), file.length() / 1024 / 1024);
            
            // 1. 初始化分片上传
            long chunkSize = 5 * 1024 * 1024; // 5MB per chunk
            String contentType = Files.probeContentType(file.toPath());
            
            MultipartInitRequest initRequest = MultipartInitRequest.builder()
                    .fileName(file.getName())
                    .fileSize(file.length())
                    .contentType(contentType)
                    .chunkSize(chunkSize)
                    .accessLevel(AccessLevel.PUBLIC)
                    .build();
            
            MultipartInitResponse initResponse = 
                    fileServiceClient.initMultipartUpload(initRequest);
            
            log.info("分片上传初始化成功! 任务ID: {}, 总分片数: {}", 
                    initResponse.getTaskId(), initResponse.getTotalChunks());
            
            // 2. 上传各个分片
            List<MultipartUploadPart> parts = new ArrayList<>();
            
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[(int) chunkSize];
                int partNumber = 1;
                int bytesRead;
                
                while ((bytesRead = fis.read(buffer)) > 0) {
                    log.info("上传分片 {}/{}", partNumber, initResponse.getTotalChunks());
                    
                    ByteArrayInputStream partStream = new ByteArrayInputStream(buffer, 0, bytesRead);
                    
                    MultipartUploadPart part = fileServiceClient.uploadPart(
                            initResponse.getTaskId(), 
                            partNumber, 
                            partStream, 
                            bytesRead);
                    
                    parts.add(part);
                    partNumber++;
                }
            }
            
            log.info("所有分片上传完成，开始合并文件");
            
            // 3. 计算文件哈希并完成上传
            String fileHash = calculateMD5(file);
            
            FileUploadResponse response = fileServiceClient.completeMultipartUpload(
                    initResponse.getTaskId(), fileHash);
            
            log.info("分片上传完成! 文件ID: {}, URL: {}", 
                    response.getFileId(), response.getUrl());
            
            return response;
            
        } catch (IOException e) {
            log.error("读取文件失败: {}", e.getMessage());
            throw new RuntimeException("读取文件失败", e);
        } catch (FileServiceException e) {
            log.error("分片上传失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 示例 6: 取消分片上传
     * 
     * 演示如何取消正在进行的分片上传
     */
    public void cancelMultipartUploadExample(String taskId) {
        try {
            log.info("取消分片上传任务: {}", taskId);
            
            fileServiceClient.cancelMultipartUpload(taskId);
            
            log.info("分片上传任务已取消");
            
        } catch (FileServiceException e) {
            log.error("取消分片上传失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 计算文件的 MD5 哈希值
     */
    private String calculateMD5(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("计算文件哈希失败", e);
        }
    }
}
