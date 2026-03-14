package com.architectcgz.file.application.service;

import com.architectcgz.file.application.dto.FileDetailResponse;
import com.architectcgz.file.application.dto.FileUrlResponse;
import com.architectcgz.file.application.service.fileaccess.command.FileAccessLevelCommandService;
import com.architectcgz.file.application.service.fileaccess.factory.FileAccessObjectFactory;
import com.architectcgz.file.application.service.fileaccess.query.FileAccessRecordQueryService;
import com.architectcgz.file.application.service.fileaccess.query.FileDetailQueryService;
import com.architectcgz.file.application.service.fileaccess.query.FileUrlQueryService;
import com.architectcgz.file.application.service.fileaccess.storage.FileAccessStorageService;
import com.architectcgz.file.application.service.fileaccess.validator.FileAccessValidator;
import com.architectcgz.file.domain.model.AccessLevel;
import com.architectcgz.file.domain.repository.FileRecordRepository;
import com.architectcgz.file.domain.repository.StorageObjectRepository;
import com.architectcgz.file.infrastructure.cache.FileUrlCacheManager;
import com.architectcgz.file.infrastructure.config.S3Properties;
import com.architectcgz.file.infrastructure.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 文件访问应用层门面。
 *
 * 为接口层收口文件 URL、详情查询与访问级别修改入口，
 * 具体实现拆分到 query/command service。
 */
@Slf4j
@Service
public class FileAccessService {

    private final FileUrlQueryService fileUrlQueryService;
    private final FileDetailQueryService fileDetailQueryService;
    private final FileAccessLevelCommandService fileAccessLevelCommandService;
    private final FileAccessStorageService fileAccessStorageService;

    @Value("${storage.access.private-url-expire-seconds:3600}")
    private int privateUrlExpireSeconds = 3600;

    @Autowired
    public FileAccessService(FileUrlQueryService fileUrlQueryService,
                             FileDetailQueryService fileDetailQueryService,
                             FileAccessLevelCommandService fileAccessLevelCommandService,
                             FileAccessStorageService fileAccessStorageService) {
        this.fileUrlQueryService = fileUrlQueryService;
        this.fileDetailQueryService = fileDetailQueryService;
        this.fileAccessLevelCommandService = fileAccessLevelCommandService;
        this.fileAccessStorageService = fileAccessStorageService;
    }

    FileAccessService(FileRecordRepository fileRecordRepository,
                      StorageObjectRepository storageObjectRepository,
                      StorageService storageService,
                      S3Properties ignoredS3Properties,
                      FileUrlCacheManager fileUrlCacheManager,
                      AccessLevelChangeTransactionHelper accessLevelChangeTransactionHelper) {
        this(
                buildLegacyRecordQueryService(fileRecordRepository, storageObjectRepository),
                new FileAccessValidator(),
                new FileAccessObjectFactory(),
                buildLegacyStorageService(storageService, fileUrlCacheManager),
                accessLevelChangeTransactionHelper
        );
    }

    private FileAccessService(FileAccessRecordQueryService fileAccessRecordQueryService,
                              FileAccessValidator fileAccessValidator,
                              FileAccessObjectFactory fileAccessObjectFactory,
                              FileAccessStorageService fileAccessStorageService,
                              AccessLevelChangeTransactionHelper accessLevelChangeTransactionHelper) {
        this(
                new FileUrlQueryService(fileAccessRecordQueryService, fileAccessValidator, fileAccessStorageService),
                new FileDetailQueryService(fileAccessRecordQueryService, fileAccessValidator, fileAccessObjectFactory),
                new FileAccessLevelCommandService(
                        fileAccessRecordQueryService,
                        fileAccessValidator,
                        fileAccessObjectFactory,
                        fileAccessStorageService,
                        accessLevelChangeTransactionHelper
                ),
                fileAccessStorageService
        );
    }

    public FileUrlResponse getFileUrl(String appId, String fileId, String requestUserId) {
        syncExpireSecondsToStorageService();
        return fileUrlQueryService.getFileUrl(appId, fileId, requestUserId);
    }

    public FileDetailResponse getFileDetail(String appId, String fileId, String requestUserId) {
        syncExpireSecondsToStorageService();
        return fileDetailQueryService.getFileDetail(appId, fileId, requestUserId);
    }

    public void updateAccessLevel(String appId, String fileId, String requestUserId, AccessLevel newLevel) {
        syncExpireSecondsToStorageService();
        fileAccessLevelCommandService.updateAccessLevel(appId, fileId, requestUserId, newLevel);
    }

    private void syncExpireSecondsToStorageService() {
        fileAccessStorageService.setPrivateUrlExpireSeconds(privateUrlExpireSeconds);
    }

    private static FileAccessRecordQueryService buildLegacyRecordQueryService(FileRecordRepository fileRecordRepository,
                                                                              StorageObjectRepository storageObjectRepository) {
        return new FileAccessRecordQueryService(fileRecordRepository, storageObjectRepository);
    }

    private static FileAccessStorageService buildLegacyStorageService(StorageService storageService,
                                                                     FileUrlCacheManager fileUrlCacheManager) {
        return new FileAccessStorageService(storageService, fileUrlCacheManager);
    }
}
