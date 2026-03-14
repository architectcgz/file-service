package com.architectcgz.file.application.service.fileaccess.transaction.mutation;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.model.StorageObject;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件访问级别切换相关的文件记录变更服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAccessRecordMutationService {

    private final FileRecordRepository fileRecordRepository;

    public void updateAccessLevelOrThrow(String fileId, AccessLevel newLevel) {
        boolean updated = fileRecordRepository.updateAccessLevel(fileId, newLevel);
        if (!updated) {
            throw new BusinessException(
                    FileServiceErrorCodes.UPDATE_ACCESS_LEVEL_FAILED,
                    String.format(FileServiceErrorMessages.UPDATE_ACCESS_LEVEL_FAILED, fileId)
            );
        }
        log.debug("Updated file access level without storage rebinding: fileId={}, newLevel={}", fileId, newLevel);
    }

    public void updateStorageBindingOrThrow(String fileId, StorageObject copiedStorageObject, AccessLevel newLevel) {
        boolean rebound = fileRecordRepository.updateStorageBindingAndAccessLevel(
                fileId,
                copiedStorageObject.getId(),
                copiedStorageObject.getStoragePath(),
                newLevel
        );
        if (!rebound) {
            throw new BusinessException(
                    FileServiceErrorCodes.UPDATE_STORAGE_BINDING_FAILED,
                    String.format(FileServiceErrorMessages.UPDATE_STORAGE_BINDING_FAILED, fileId)
            );
        }
    }
}
