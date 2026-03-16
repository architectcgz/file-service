package com.architectcgz.file.application.service.upload.file;

import java.nio.file.Path;

/**
 * 表单上传源文件的预处理结果。
 *
 * 包含临时文件路径，以及在落盘阶段顺手提取出的文件头和哈希。
 */
public record PreparedUploadSource(
        Path file,
        byte[] header,
        String hash
) {
}
