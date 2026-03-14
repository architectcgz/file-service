package com.architectcgz.file.application.service.fileaccess.transaction;

import com.architectcgz.file.application.service.fileaccess.transaction.mutation.FileAccessRecordMutationService;
import com.architectcgz.file.domain.model.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessLevelOnlyTransactionService {

    private final FileAccessRecordMutationService fileAccessRecordMutationService;

    @Transactional(rollbackFor = Exception.class)
    public void updateAccessLevelOnly(String fileId, AccessLevel newLevel) {
        fileAccessRecordMutationService.updateAccessLevelOrThrow(fileId, newLevel);
    }
}
