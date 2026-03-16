package com.platform.fileservice.contract.upload.model;

import java.util.List;
import java.util.Map;

/**
 * Response payload for creating or reusing a v2 upload session.
 */
public record CreateUploadSessionResponse(
        UploadSessionView uploadSession,
        boolean resumed,
        boolean instantUpload,
        List<Integer> completedPartNumbers,
        List<UploadedPartView> completedPartInfos,
        String singleUploadUrl,
        String singleUploadMethod,
        Integer singleUploadExpiresInSeconds,
        Map<String, String> singleUploadHeaders
) {
}
