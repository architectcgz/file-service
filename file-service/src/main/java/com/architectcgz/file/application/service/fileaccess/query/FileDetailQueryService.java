package com.architectcgz.file.application.service.fileaccess.query;

import com.architectcgz.file.application.dto.FileDetailResponse;
import com.architectcgz.file.application.service.fileaccess.factory.FileAccessObjectFactory;
import com.architectcgz.file.application.service.fileaccess.validator.FileAccessValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileDetailQueryService {

    private final FileAccessRecordQueryService fileAccessRecordQueryService;
    private final FileAccessValidator fileAccessValidator;
    private final FileAccessObjectFactory fileAccessObjectFactory;

    public FileDetailResponse getFileDetail(String appId, String fileId, String requestUserId) {
        var file = fileAccessRecordQueryService.findFileOrThrow(fileId);
        fileAccessValidator.validateFileAccess(file, fileId, requestUserId, appId);
        return fileAccessObjectFactory.buildFileDetailResponse(file);
    }
}
