package com.platform.fileservice.core.ports.access;

import com.platform.fileservice.core.domain.model.FileAsset;

/**
 * Port for loading a file that the caller is already authorized to access.
 */
public interface AuthorizedFileAccessPort {

    FileAsset loadAccessibleFile(String tenantId, String fileId, String subjectId);
}
