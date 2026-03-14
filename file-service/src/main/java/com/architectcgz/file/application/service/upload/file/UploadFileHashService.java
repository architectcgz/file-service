package com.architectcgz.file.application.service.upload.file;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 上传文件哈希服务。
 */
@Slf4j
@Component
public class UploadFileHashService {

    public String calculateHash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(data);
            return toHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 algorithm not available", e);
            throw new BusinessException(FileServiceErrorCodes.FILE_HASH_FAILED, FileServiceErrorMessages.FILE_HASH_FAILED);
        }
    }

    public String calculateHash(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            try (InputStream is = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(is, md)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // Stream through file to avoid loading it fully into memory.
                }
            }
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 algorithm not available", e);
            throw new BusinessException(FileServiceErrorCodes.FILE_HASH_FAILED, FileServiceErrorMessages.FILE_HASH_FAILED);
        } catch (IOException e) {
            log.error("Failed to calculate file hash: {}", file, e);
            throw new BusinessException(
                    FileServiceErrorCodes.FILE_HASH_FAILED,
                    FileServiceErrorMessages.FILE_HASH_FAILED + ": " + e.getMessage()
            );
        }
    }

    private String toHex(byte[] hashBytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
