package com.architectcgz.file.application.service.direct.query;

import com.architectcgz.file.application.dto.DirectUploadPartUrlRequest;
import com.architectcgz.file.application.dto.DirectUploadPartUrlResponse;
import com.architectcgz.file.application.service.direct.storage.DirectUploadStorageService;
import com.architectcgz.file.application.service.direct.validator.DirectUploadTaskValidator;
import com.architectcgz.file.application.service.uploadtask.query.UploadTaskQueryService;
import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.infrastructure.config.AccessProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectUploadPartUrlQueryService {

    private final DirectUploadTaskValidator directUploadTaskValidator;
    private final DirectUploadStorageService directUploadStorageService;
    private final UploadTaskQueryService uploadTaskQueryService;
    private final AccessProperties accessProperties;

    public DirectUploadPartUrlResponse getPartUploadUrls(String appId, DirectUploadPartUrlRequest request, String userId) {
        log.info("获取分片上传URL: taskId={}, partNumbers={}", request.getTaskId(), request.getPartNumbers());

        var task = uploadTaskQueryService.getById(request.getTaskId());

        directUploadTaskValidator.validateTaskAccess(task, appId, userId, FileServiceErrorMessages.ACCESS_DENIED_UPLOAD_TASK);
        directUploadTaskValidator.ensureTaskUploadingAndNotExpired(task);
        directUploadTaskValidator.validateUniquePartNumbers(request.getPartNumbers());

        List<DirectUploadPartUrlResponse.PartUrl> partUrls = new ArrayList<>();
        for (Integer partNumber : request.getPartNumbers()) {
            if (partNumber < 1 || partNumber > task.getTotalParts()) {
                throw new BusinessException(
                        FileServiceErrorCodes.PART_NUMBER_INVALID,
                        String.format(FileServiceErrorMessages.PART_NUMBER_INVALID, partNumber)
                );
            }

            int expireSeconds = accessProperties.getPresignedUrlExpireSeconds();
            String presignedUrl = directUploadStorageService.generatePresignedUploadPartUrl(
                    task.getStoragePath(),
                    task.getUploadId(),
                    partNumber,
                    expireSeconds,
                    directUploadStorageService.resolveUploadBucketName()
            );

            partUrls.add(DirectUploadPartUrlResponse.PartUrl.builder()
                    .partNumber(partNumber)
                    .uploadUrl(presignedUrl)
                    .expiresIn(expireSeconds)
                    .build());
        }

        return DirectUploadPartUrlResponse.builder()
                .taskId(task.getId())
                .partUrls(partUrls)
                .build();
    }
}
