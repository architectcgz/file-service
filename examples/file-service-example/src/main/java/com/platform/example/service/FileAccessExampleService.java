package com.platform.example.service;

import com.platform.fileservice.client.FileServiceClient;
import com.platform.fileservice.client.exception.AccessDeniedException;
import com.platform.fileservice.client.exception.FileNotFoundException;
import com.platform.fileservice.client.exception.FileServiceException;
import com.platform.fileservice.client.model.FileDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件访问示例服务
 * 
 * 演示如何获取文件信息和访问 URL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAccessExampleService {

    private final FileServiceClient fileServiceClient;

    /**
     * 示例 1: 获取文件访问 URL
     * 
     * 演示如何获取文件的访问 URL
     */
    public String getFileUrlExample(String fileId) {
        try {
            log.info("获取文件访问 URL: {}", fileId);
            
            String fileUrl = fileServiceClient.getFileUrl(fileId);
            
            log.info("文件 URL: {}", fileUrl);
            
            return fileUrl;
            
        } catch (FileNotFoundException e) {
            log.error("文件不存在: {}", e.getMessage());
            throw e;
        } catch (AccessDeniedException e) {
            log.error("无权访问该文件: {}", e.getMessage());
            throw e;
        } catch (FileServiceException e) {
            log.error("获取文件 URL 失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 示例 2: 获取文件详细信息
     * 
     * 演示如何获取文件的完整元数据
     */
    public FileDetailResponse getFileDetailExample(String fileId) {
        try {
            log.info("获取文件详细信息: {}", fileId);
            
            FileDetailResponse detail = fileServiceClient.getFileDetail(fileId);
            
            log.info("文件详情 - ID: {}, 名称: {}, 大小: {} bytes, 类型: {}, 创建时间: {}", 
                    detail.getFileId(),
                    detail.getOriginalName(),
                    detail.getFileSize(),
                    detail.getContentType(),
                    detail.getCreatedAt());
            
            return detail;
            
        } catch (FileNotFoundException e) {
            log.error("文件不存在: {}", e.getMessage());
            throw e;
        } catch (AccessDeniedException e) {
            log.error("无权访问该文件: {}", e.getMessage());
            throw e;
        } catch (FileServiceException e) {
            log.error("获取文件详情失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 示例 3: 删除文件
     * 
     * 演示如何删除文件
     */
    public void deleteFileExample(String fileId) {
        try {
            log.info("删除文件: {}", fileId);
            
            fileServiceClient.deleteFile(fileId);
            
            log.info("文件删除成功");
            
        } catch (FileNotFoundException e) {
            log.error("文件不存在: {}", e.getMessage());
            throw e;
        } catch (AccessDeniedException e) {
            log.error("无权删除该文件: {}", e.getMessage());
            throw e;
        } catch (FileServiceException e) {
            log.error("删除文件失败: {}", e.getMessage());
            throw e;
        }
    }
}
