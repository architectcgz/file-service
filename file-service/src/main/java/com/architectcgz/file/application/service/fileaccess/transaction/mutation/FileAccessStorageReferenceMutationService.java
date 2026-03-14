package com.architectcgz.file.application.service.fileaccess.transaction.mutation;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 文件访问级别切换相关的存储引用变更服务。
 */
@Service
@RequiredArgsConstructor
public class FileAccessStorageReferenceMutationService {

    private final StorageObjectRepository storageObjectRepository;

    public void decrementReferenceCountOrThrow(String sourceStorageObjectId) {
        boolean decremented = storageObjectRepository.decrementReferenceCount(sourceStorageObjectId);
        if (!decremented) {
            throw new BusinessException(
                    FileServiceErrorCodes.STORAGE_REFERENCE_DECREMENT_FAILED,
                    String.format(FileServiceErrorMessages.STORAGE_REFERENCE_DECREMENT_FAILED, sourceStorageObjectId)
            );
        }
    }
}
