package com.architectcgz.file.integration.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试上下文类
 * 用于管理测试过程中创建的文件和数据，支持测试清理
 */
public class TestContext {
    
    private static final Logger log = LoggerFactory.getLogger(TestContext.class);
    
    private String appId;
    private String userId;
    private final List<TestFileInfo> uploadedFiles;
    private final List<String> uploadTaskIds;
    
    public TestContext() {
        this.uploadedFiles = new CopyOnWriteArrayList<>();
        this.uploadTaskIds = new CopyOnWriteArrayList<>();
    }
    
    public TestContext(String appId, String userId) {
        this();
        this.appId = appId;
        this.userId = userId;
    }
    
    /**
     * 添加已上传的文件信息
     * 
     * @param fileInfo 文件信息
     */
    public void addUploadedFile(TestFileInfo fileInfo) {
        uploadedFiles.add(fileInfo);
        log.debug("Added uploaded file to context: fileId={}, filename={}", 
                 fileInfo.getFileId(), fileInfo.getFilename());
    }
    
    /**
     * 添加上传任务 ID
     * 
     * @param uploadTaskId 上传任务 ID
     */
    public void addUploadTaskId(String uploadTaskId) {
        uploadTaskIds.add(uploadTaskId);
        log.debug("Added upload task ID to context: uploadTaskId={}", uploadTaskId);
    }
    
    /**
     * 获取所有已上传的文件
     * 
     * @return 文件列表
     */
    public List<TestFileInfo> getUploadedFiles() {
        return new ArrayList<>(uploadedFiles);
    }
    
    /**
     * 获取所有上传任务 ID
     * 
     * @return 上传任务 ID 列表
     */
    public List<String> getUploadTaskIds() {
        return new ArrayList<>(uploadTaskIds);
    }
    
    /**
     * 清理测试数据
     * 清空所有记录的文件和任务
     */
    public void clear() {
        int fileCount = uploadedFiles.size();
        int taskCount = uploadTaskIds.size();
        
        uploadedFiles.clear();
        uploadTaskIds.clear();
        
        log.debug("Cleared test context: files={}, tasks={}", fileCount, taskCount);
    }
    
    /**
     * 检查是否有待清理的数据
     * 
     * @return true 如果有待清理的数据
     */
    public boolean hasDataToCleanup() {
        return !uploadedFiles.isEmpty() || !uploadTaskIds.isEmpty();
    }
    
    /**
     * 获取文件数量
     * 
     * @return 文件数量
     */
    public int getFileCount() {
        return uploadedFiles.size();
    }
    
    /**
     * 获取任务数量
     * 
     * @return 任务数量
     */
    public int getTaskCount() {
        return uploadTaskIds.size();
    }
    
    // Getters and Setters
    
    public String getAppId() {
        return appId;
    }
    
    public void setAppId(String appId) {
        this.appId = appId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    /**
     * 测试文件信息类
     */
    public static class TestFileInfo {
        private String fileId;
        private String filename;
        private String contentType;
        private long size;
        private byte[] content;
        private String url;
        private String storagePath;
        
        public TestFileInfo() {
        }
        
        public TestFileInfo(String fileId, String filename) {
            this.fileId = fileId;
            this.filename = filename;
        }
        
        // Getters and Setters
        
        public String getFileId() {
            return fileId;
        }
        
        public void setFileId(String fileId) {
            this.fileId = fileId;
        }
        
        public String getFilename() {
            return filename;
        }
        
        public void setFilename(String filename) {
            this.filename = filename;
        }
        
        public String getContentType() {
            return contentType;
        }
        
        public void setContentType(String contentType) {
            this.contentType = contentType;
        }
        
        public long getSize() {
            return size;
        }
        
        public void setSize(long size) {
            this.size = size;
        }
        
        public byte[] getContent() {
            return content;
        }
        
        public void setContent(byte[] content) {
            this.content = content;
        }
        
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
        
        public String getStoragePath() {
            return storagePath;
        }
        
        public void setStoragePath(String storagePath) {
            this.storagePath = storagePath;
        }
        
        @Override
        public String toString() {
            return "TestFileInfo{" +
                    "fileId='" + fileId + '\'' +
                    ", filename='" + filename + '\'' +
                    ", contentType='" + contentType + '\'' +
                    ", size=" + size +
                    ", url='" + url + '\'' +
                    ", storagePath='" + storagePath + '\'' +
                    '}';
        }
    }
}
