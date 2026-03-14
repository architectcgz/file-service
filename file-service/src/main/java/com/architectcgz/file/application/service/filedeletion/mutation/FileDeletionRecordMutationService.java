package com.architectcgz.file.application.service.filedeletion.mutation;

import com.architectcgz.file.common.constant.FileServiceErrorCodes;
import com.architectcgz.file.common.constant.FileServiceErrorMessages;
import com.architectcgz.file.common.exception.BusinessException;
import com.architectcgz.file.domain.model.FileStatus;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件删除相关的文件记录变更服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDeletionRecordMutationService {

    private final FileRecordRepository fileRecordRepository;

    public void markDeleted(String fileRecordId) {
        fileRecordRepository.updateStatus(fileRecordId, FileStatus.DELETED);
        log.debug("文件记录已标记删除: fileRecordId={}", fileRecordId);
    }

    public void deleteOrThrow(String fileId) {
        boolean deleted = fileRecordRepository.deleteById(fileId);
        if (!deleted) {
            throw new BusinessException(
                    FileServiceErrorCodes.FILE_DELETE_FAILED,
                    String.format(FileServiceErrorMessages.FILE_DELETE_FAILED, fileId)
            );
        }
        log.debug("文件记录已删除: fileId={}", fileId);
    }
}
