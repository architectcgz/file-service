package com.architectcgz.file.integration.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * URL 访问验证工具类
 * 用于验证文件 URL 的可访问性和内容
 */
public class URLAccessVerifier {
    
    private static final Logger log = LoggerFactory.getLogger(URLAccessVerifier.class);
    
    private static final int CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final int READ_TIMEOUT = 30000; // 30 seconds
    private static final int BUFFER_SIZE = 8192;
    
    /**
     * 通过 URL 下载文件内容
     * 
     * @param urlString 文件 URL
     * @return 文件内容字节数组
     */
    public byte[] downloadFile(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            int statusCode = connection.getResponseCode();
            
            if (statusCode != HttpURLConnection.HTTP_OK) {
                log.error("Failed to download file: url={}, statusCode={}", urlString, statusCode);
                throw new RuntimeException("Failed to download file, status code: " + statusCode);
            }
            
            try (InputStream inputStream = connection.getInputStream();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                
                byte[] content = baos.toByteArray();
                log.debug("Downloaded file from URL: url={}, size={} bytes", urlString, content.length);
                
                return content;
            }
        } catch (IOException e) {
            log.error("Error downloading file from URL: url={}", urlString, e);
            throw new RuntimeException("Failed to download file from URL", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * 检查 URL 是否可访问
     * 
     * @param urlString 文件 URL
     * @return true 如果 URL 返回 200 状态码，否则 false
     */
    public boolean isAccessible(String urlString) {
        try {
            int statusCode = getStatusCode(urlString);
            boolean accessible = statusCode == HttpURLConnection.HTTP_OK;
            
            log.debug("URL accessibility check: url={}, statusCode={}, accessible={}", 
                     urlString, statusCode, accessible);
            
            return accessible;
        } catch (Exception e) {
            log.debug("URL is not accessible: url={}, error={}", urlString, e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取 URL 的 HTTP 状态码
     * 
     * @param urlString 文件 URL
     * @return HTTP 状态码
     */
    public int getStatusCode(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD"); // Use HEAD to avoid downloading content
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            int statusCode = connection.getResponseCode();
            log.debug("Got status code from URL: url={}, statusCode={}", urlString, statusCode);
            
            return statusCode;
        } catch (IOException e) {
            log.error("Error getting status code from URL: url={}", urlString, e);
            throw new RuntimeException("Failed to get status code from URL", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * 获取 URL 的 Content-Type
     * 
     * @param urlString 文件 URL
     * @return Content-Type
     */
    public String getContentType(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            int statusCode = connection.getResponseCode();
            
            if (statusCode != HttpURLConnection.HTTP_OK) {
                log.error("Failed to get content type: url={}, statusCode={}", urlString, statusCode);
                throw new RuntimeException("Failed to get content type, status code: " + statusCode);
            }
            
            String contentType = connection.getContentType();
            log.debug("Got content type from URL: url={}, contentType={}", urlString, contentType);
            
            return contentType;
        } catch (IOException e) {
            log.error("Error getting content type from URL: url={}", urlString, e);
            throw new RuntimeException("Failed to get content type from URL", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * 获取 URL 的 Content-Length
     * 
     * @param urlString 文件 URL
     * @return Content-Length（字节）
     */
    public long getContentLength(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            int statusCode = connection.getResponseCode();
            
            if (statusCode != HttpURLConnection.HTTP_OK) {
                log.error("Failed to get content length: url={}, statusCode={}", urlString, statusCode);
                throw new RuntimeException("Failed to get content length, status code: " + statusCode);
            }
            
            long contentLength = connection.getContentLengthLong();
            log.debug("Got content length from URL: url={}, contentLength={} bytes", 
                     urlString, contentLength);
            
            return contentLength;
        } catch (IOException e) {
            log.error("Error getting content length from URL: url={}", urlString, e);
            throw new RuntimeException("Failed to get content length from URL", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * 验证 URL 是否返回指定的状态码
     * 
     * @param urlString 文件 URL
     * @param expectedStatusCode 期望的状态码
     * @return true 如果状态码匹配，否则 false
     */
    public boolean verifyStatusCode(String urlString, int expectedStatusCode) {
        try {
            int actualStatusCode = getStatusCode(urlString);
            boolean matches = actualStatusCode == expectedStatusCode;
            
            log.debug("Status code verification: url={}, expected={}, actual={}, matches={}", 
                     urlString, expectedStatusCode, actualStatusCode, matches);
            
            return matches;
        } catch (Exception e) {
            log.debug("Status code verification failed: url={}, expected={}, error={}", 
                     urlString, expectedStatusCode, e.getMessage());
            return false;
        }
    }
}
