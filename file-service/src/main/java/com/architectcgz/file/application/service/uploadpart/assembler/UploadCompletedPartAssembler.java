package com.architectcgz.file.application.service.uploadpart.assembler;

import com.architectcgz.file.infrastructure.repository.po.UploadPartPO;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.Comparator;
import java.util.List;

/**
 * 已完成分片装配器。
 */
@Component
public class UploadCompletedPartAssembler {

    public List<CompletedPart> toCompletedParts(List<UploadPartPO> uploadPartPOs) {
        return uploadPartPOs.stream()
                .sorted(Comparator.comparingInt(UploadPartPO::getPartNumber))
                .map(this::toCompletedPart)
                .toList();
    }

    private CompletedPart toCompletedPart(UploadPartPO uploadPartPO) {
        return CompletedPart.builder()
                .partNumber(uploadPartPO.getPartNumber())
                .eTag(uploadPartPO.getEtag())
                .build();
    }
}
