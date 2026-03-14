package com.architectcgz.file.application.service.filemanagement.query;

import com.architectcgz.file.application.dto.FileQuery;
import com.architectcgz.file.common.exception.FileNotFoundException;
import com.architectcgz.file.common.result.PageResponse;
import com.architectcgz.file.domain.model.FileRecord;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文件管理记录查询服务。
 */
@Service
@RequiredArgsConstructor
public class FileManagementRecordQueryService {

    private final FileRecordRepository fileRecordRepository;

    public PageResponse<FileRecord> listFiles(FileQuery query) {
        List<FileRecord> files = fileRecordRepository.findByQuery(query);
        long total = fileRecordRepository.countByQuery(query);
        return PageResponse.of(files, query.getPage(), query.getSize(), total);
    }

    public FileRecord findFileOrThrow(String fileId) {
        return fileRecordRepository.findById(fileId)
                .orElseThrow(() -> FileNotFoundException.notFound(fileId));
    }
}
