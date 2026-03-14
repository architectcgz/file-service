package com.architectcgz.file.application.service.upload.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 上传临时文件服务。
 */
@Slf4j
@Component
public class UploadTempFileService {

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
}
