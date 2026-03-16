package com.platform.fileservice.core.ports.access;

import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.BlobObject;
import com.platform.fileservice.core.domain.model.FileAccessMutationContext;

/**
 * Port for mutating file access policy against the persistence model.
 */
public interface FileAccessMutationPort {

    FileAccessMutationContext loadForChange(String tenantId, String fileId);

    void updateAccessLevel(String fileId, AccessLevel accessLevel);

    void createBlobObject(BlobObject blobObject);

    void rebindBlobAndAccessLevel(String fileId, BlobObject blobObject, AccessLevel accessLevel);

    void decrementBlobReference(String blobObjectId);
}
