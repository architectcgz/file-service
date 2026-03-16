package com.architectcgz.file.application.service.upload.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * 上传临时文件服务。
 */
@Slf4j
@Component
public class UploadTempFileService {

    private static final int STREAM_BUFFER_SIZE = 8192;
    private static final String HASH_ALGORITHM = "MD5";

    public PreparedUploadSource prepareMultipartFile(MultipartFile multipartFile,
                                                     String prefix,
                                                     String suffix,
                                                     int headerLength,
                                                     boolean calculateHash) throws IOException {
        Path tempFile = Files.createTempFile(prefix, suffix);
        MessageDigest messageDigest = calculateHash ? createMessageDigest() : null;
        byte[] headerBuffer = headerLength <= 0 ? new byte[0] : new byte[headerLength];
        int headerBytesRead = 0;

        try (InputStream inputStream = multipartFile.getInputStream();
             OutputStream outputStream = Files.newOutputStream(tempFile)) {
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                if (messageDigest != null) {
                    messageDigest.update(buffer, 0, bytesRead);
                }
                if (headerBytesRead < headerBuffer.length) {
                    int headerCopyLength = Math.min(bytesRead, headerBuffer.length - headerBytesRead);
                    System.arraycopy(buffer, 0, headerBuffer, headerBytesRead, headerCopyLength);
                    headerBytesRead += headerCopyLength;
                }
            }
        } catch (IOException | RuntimeException ex) {
            deleteQuietly(tempFile);
            throw ex;
        }

        byte[] header = headerBytesRead == headerBuffer.length
                ? headerBuffer
                : Arrays.copyOf(headerBuffer, headerBytesRead);
        String hash = messageDigest == null ? null : toHex(messageDigest.digest());
        return new PreparedUploadSource(tempFile, header, hash);
    }

    public byte[] readHeader(Path file, int length) throws IOException {
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

    public void deleteQuietly(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.warn("Failed to delete temp file: {}", tempFile, e);
        }
    }

    private MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 algorithm not available", ex);
        }
    }

    private String toHex(byte[] hashBytes) {
        StringBuilder builder = new StringBuilder(hashBytes.length * 2);
        for (byte hashByte : hashBytes) {
            builder.append(String.format("%02x", hashByte));
        }
        return builder.toString();
    }
}
