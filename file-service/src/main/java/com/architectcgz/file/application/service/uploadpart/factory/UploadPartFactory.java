package com.architectcgz.file.application.service.uploadpart.factory;

import com.architectcgz.file.domain.model.UploadPart;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 分片记录对象工厂。
 */
@Component
public class UploadPartFactory {

    public UploadPart createUploadPart(String taskId, int partNumber, String etag, long size) {
        UploadPart part = new UploadPart();
        part.setId(UuidCreator.getTimeOrderedEpoch().toString());
        part.setTaskId(taskId);
        part.setPartNumber(partNumber);
        part.setEtag(etag);
        part.setSize(size);
        part.setUploadedAt(LocalDateTime.now());
        return part;
    }
}
