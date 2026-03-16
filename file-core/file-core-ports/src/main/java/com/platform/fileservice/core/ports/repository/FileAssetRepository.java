package com.platform.fileservice.core.ports.repository;

import com.platform.fileservice.core.domain.model.FileAsset;

import java.util.Optional;

/**
 * Repository port for logical file assets.
 */
public interface FileAssetRepository {

    Optional<FileAsset> findById(String fileId);

    FileAsset save(FileAsset fileAsset);
}
