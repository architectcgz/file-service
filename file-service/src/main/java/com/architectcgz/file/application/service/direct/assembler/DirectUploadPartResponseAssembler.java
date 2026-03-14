package com.architectcgz.file.application.service.direct.assembler;

import com.architectcgz.file.application.dto.DirectUploadInitResponse;
import com.architectcgz.file.infrastructure.storage.S3StorageService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 直传分片响应装配器。
 */
@Component
public class DirectUploadPartResponseAssembler {

    public List<Integer> extractCompletedPartNumbers(List<S3StorageService.PartInfo> partInfos) {
        return partInfos.stream()
                .map(S3StorageService.PartInfo::getPartNumber)
                .toList();
    }

    public List<DirectUploadInitResponse.PartInfo> toResponsePartInfos(List<S3StorageService.PartInfo> s3PartInfos) {
        return s3PartInfos.stream()
                .map(info -> DirectUploadInitResponse.PartInfo.builder()
                        .partNumber(info.getPartNumber())
                        .etag(info.getEtag())
                        .build())
                .toList();
    }
}
