package com.architectcgz.file.application.service.instantupload.assembler;

import com.architectcgz.file.application.dto.InstantUploadCheckResponse;
import org.springframework.stereotype.Component;

/**
 * 秒传响应装配器。
 */
@Component
public class InstantUploadResponseAssembler {

    public InstantUploadCheckResponse successResponse(String fileId, String fileUrl) {
        return InstantUploadCheckResponse.success(fileId, fileUrl);
    }

    public InstantUploadCheckResponse needUploadResponse() {
        return InstantUploadCheckResponse.needUpload();
    }
}
