package com.platform.fileservice.core.application.service;

import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.ports.repository.FileAssetRepository;

import java.util.Optional;

/**
 * Application service entry for administrative file inspection.
 */
public final class FileAdminAppService {

    private final FileAssetRepository fileAssetRepository;

    public FileAdminAppService(FileAssetRepository fileAssetRepository) {
        this.fileAssetRepository = fileAssetRepository;
    }

    public Optional<FileAsset> getFileAsset(String fileId) {
        return fileAssetRepository.findById(fileId);
    }
}
