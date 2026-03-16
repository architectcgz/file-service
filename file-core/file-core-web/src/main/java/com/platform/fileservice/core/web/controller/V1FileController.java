package com.platform.fileservice.core.web.controller;

import com.platform.fileservice.contract.access.model.AccessTicketView;
import com.platform.fileservice.contract.access.model.BatchAccessTicketItemView;
import com.platform.fileservice.contract.access.model.BatchAccessTicketView;
import com.platform.fileservice.contract.access.model.BatchIssueAccessTicketsRequest;
import com.platform.fileservice.contract.common.FileServiceErrorCode;
import com.platform.fileservice.contract.files.model.AccessLevelView;
import com.platform.fileservice.contract.files.model.ChangeAccessLevelRequest;
import com.platform.fileservice.contract.files.model.FileAssetView;
import com.platform.fileservice.core.application.service.AccessAppService;
import com.platform.fileservice.core.domain.exception.FileAccessDeniedException;
import com.platform.fileservice.core.domain.exception.FileAssetNotFoundException;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.AccessTicketGrant;
import com.platform.fileservice.core.domain.model.FileAsset;
import com.platform.fileservice.core.web.config.FileCoreAccessProperties;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * V1 文件访问接口，读写链路由 core modules 提供。
 */
@RestController
@RequestMapping("/api/v1")
public class V1FileController {

    private final AccessAppService accessAppService;
    private final FileCoreAccessProperties fileCoreAccessProperties;

    public V1FileController(AccessAppService accessAppService,
                            FileCoreAccessProperties fileCoreAccessProperties) {
        this.accessAppService = accessAppService;
        this.fileCoreAccessProperties = fileCoreAccessProperties;
    }

    @GetMapping("/files/{fileId}")
    public FileAssetView getFileDetail(@PathVariable String fileId,
                                       @RequestHeader("X-App-Id") String tenantId,
                                       @RequestHeader(value = "X-User-Id", required = false) String subjectId) {
        return toView(accessAppService.getAccessibleFile(tenantId, fileId, normalizeSubject(subjectId)));
    }

    @PostMapping("/files/{fileId}:issue-access-ticket")
    public AccessTicketView issueAccessTicket(@PathVariable String fileId,
                                              @RequestHeader("X-App-Id") String tenantId,
                                              @RequestHeader(value = "X-User-Id", required = false) String subjectId) {
        AccessTicketGrant grant = accessAppService.issueAccessTicket(
                fileId,
                tenantId,
                normalizeSubject(subjectId),
                fileCoreAccessProperties.getTicketTtl()
        );

        return toAccessTicketView(grant);
    }

    @PostMapping("/files:batch-issue-access-ticket")
    public BatchAccessTicketView issueAccessTickets(@Valid @RequestBody BatchIssueAccessTicketsRequest request,
                                                    @RequestHeader("X-App-Id") String tenantId,
                                                    @RequestHeader(value = "X-User-Id", required = false) String subjectId) {
        String normalizedSubjectId = normalizeSubject(subjectId);
        List<BatchAccessTicketItemView> items = new ArrayList<>();
        for (String fileId : normalizeFileIds(request.fileIds())) {
            try {
                AccessTicketGrant grant = accessAppService.issueAccessTicket(
                        fileId,
                        tenantId,
                        normalizedSubjectId,
                        fileCoreAccessProperties.getTicketTtl()
                );
                items.add(new BatchAccessTicketItemView(
                        grant.fileId(),
                        grant.serializedTicket(),
                        buildGatewayUrl(grant),
                        grant.expiresAt(),
                        null,
                        null
                ));
            } catch (FileAssetNotFoundException ex) {
                items.add(failedTicketItem(fileId, FileServiceErrorCode.FILE_NOT_FOUND, ex.getMessage()));
            } catch (FileAccessDeniedException ex) {
                items.add(failedTicketItem(fileId, FileServiceErrorCode.ACCESS_DENIED, ex.getMessage()));
            }
        }
        return new BatchAccessTicketView(List.copyOf(items));
    }

    private FileAssetView toView(FileAsset fileAsset) {
        return new FileAssetView(
                fileAsset.fileId(),
                fileAsset.tenantId(),
                fileAsset.ownerId(),
                fileAsset.originalFilename(),
                fileAsset.contentType(),
                fileAsset.fileSize(),
                AccessLevelView.valueOf(fileAsset.accessLevel().name()),
                fileAsset.status().name()
        );
    }

    @PostMapping("/files/{fileId}:change-access-level")
    public void changeAccessLevel(@PathVariable String fileId,
                                  @RequestHeader("X-App-Id") String tenantId,
                                  @RequestHeader("X-User-Id") String subjectId,
                                  @Valid @RequestBody ChangeAccessLevelRequest request) {
        AccessLevel accessLevel = AccessLevel.valueOf(request.accessLevel().name());
        accessAppService.updateAccessLevel(tenantId, fileId, subjectId, accessLevel);
    }

    private String normalizeSubject(String subjectId) {
        return StringUtils.hasText(subjectId) ? subjectId.trim() : null;
    }

    private List<String> normalizeFileIds(List<String> fileIds) {
        List<String> normalizedFileIds = new ArrayList<>(fileIds.size());
        for (String fileId : fileIds) {
            normalizedFileIds.add(fileId.trim());
        }
        return List.copyOf(normalizedFileIds);
    }

    private AccessTicketView toAccessTicketView(AccessTicketGrant grant) {
        return new AccessTicketView(
                grant.serializedTicket(),
                buildGatewayUrl(grant),
                grant.expiresAt()
        );
    }

    private String buildGatewayUrl(AccessTicketGrant grant) {
        return UriComponentsBuilder.fromUriString(fileCoreAccessProperties.getGatewayBaseUrl())
                .path("/api/v1/files/{fileId}/content")
                .queryParam("ticket", grant.serializedTicket())
                .buildAndExpand(grant.fileId())
                .toUriString();
    }

    private BatchAccessTicketItemView failedTicketItem(String fileId,
                                                       FileServiceErrorCode errorCode,
                                                       String message) {
        return new BatchAccessTicketItemView(
                fileId,
                null,
                null,
                null,
                errorCode,
                message
        );
    }
}
