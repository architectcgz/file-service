package com.architectcgz.file.application.service.filedeletion.query;

import com.architectcgz.file.application.service.filedeletion.FileDeleteTransactionSupport;
import com.architectcgz.file.domain.model.StorageObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StorageObjectLastReferenceQueryService {

    private final FileDeleteTransactionSupport fileDeleteTransactionSupport;

    public Optional<StorageObject> findStorageObjectIfLastReference(String storageObjectId) {
        return fileDeleteTransactionSupport.findStorageObjectIfLastReference(storageObjectId);
    }
}
