package com.platform.fileservice.core.web.controller;

import com.platform.fileservice.contract.files.model.AccessLevelView;
import com.platform.fileservice.contract.upload.model.CreatePartUploadUrlsRequest;
import com.platform.fileservice.contract.upload.model.CreateUploadSessionResponse;
import com.platform.fileservice.contract.upload.model.CreateUploadSessionRequest;
import com.platform.fileservice.contract.upload.model.CompleteUploadSessionRequest;
import com.platform.fileservice.contract.upload.model.PartUploadUrlView;
import com.platform.fileservice.contract.upload.model.PartUploadUrlsView;
import com.platform.fileservice.contract.upload.model.UploadCompletionView;
import com.platform.fileservice.contract.upload.model.UploadModeView;
import com.platform.fileservice.contract.upload.model.UploadProgressView;
import com.platform.fileservice.contract.upload.model.UploadSessionStatusView;
import com.platform.fileservice.contract.upload.model.UploadSessionView;
import com.platform.fileservice.contract.upload.model.UploadedPartView;
import com.platform.fileservice.core.application.service.UploadAppService;
import com.platform.fileservice.core.domain.model.AccessLevel;
import com.platform.fileservice.core.domain.model.CompletedUploadPart;
import com.platform.fileservice.core.domain.model.PartUploadUrlGrant;
import com.platform.fileservice.core.domain.model.SingleUploadUrlGrant;
import com.platform.fileservice.core.domain.model.UploadCompletion;
import com.platform.fileservice.core.domain.model.UploadMode;
import com.platform.fileservice.core.domain.model.UploadProgress;
import com.platform.fileservice.core.domain.model.UploadSession;
import com.platform.fileservice.core.domain.model.UploadSessionCreationResult;
import com.platform.fileservice.core.web.config.FileCoreUploadProperties;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * V1 upload-session endpoints backed by the core modules.
 */
@RestController
@RequestMapping("/api/v1/upload-sessions")
public class V1UploadSessionController {

    private final UploadAppService uploadAppService;
    private final FileCoreUploadProperties fileCoreUploadProperties;

    public V1UploadSessionController(UploadAppService uploadAppService,
                                     FileCoreUploadProperties fileCoreUploadProperties) {
        this.uploadAppService = uploadAppService;
        this.fileCoreUploadProperties = fileCoreUploadProperties;
    }

    @PostMapping
    public CreateUploadSessionResponse createUploadSession(@RequestHeader("X-App-Id") String tenantId,
                                                           @RequestHeader("X-User-Id") String subjectId,
                                                           @Valid @RequestBody CreateUploadSessionRequest request) {
        UploadMode resolvedUploadMode = resolveUploadMode(request);
        UploadSessionCreationResult creationResult = uploadAppService.createSession(
                tenantId,
                subjectId,
                resolvedUploadMode,
                AccessLevel.valueOf(request.accessLevel().name()),
                request.originalFilename(),
                request.contentType(),
                request.expectedSize(),
                request.fileHash(),
                fileCoreUploadProperties.getSessionTtl(),
                fileCoreUploadProperties.getChunkSizeBytes(),
                fileCoreUploadProperties.getMaxParts()
        );
        SingleUploadUrlGrant singleUploadUrlGrant = creationResult.instantUpload()
                ? null
                : maybeIssueSingleUploadUrl(tenantId, subjectId, creationResult.uploadSession());
        return new CreateUploadSessionResponse(
                toView(creationResult.uploadSession()),
                creationResult.resumed(),
                creationResult.instantUpload(),
                creationResult.uploadedParts().stream().map(uploadedPart -> uploadedPart.partNumber()).toList(),
                creationResult.uploadedParts().stream()
                        .map(uploadedPart -> new UploadedPartView(
                                uploadedPart.partNumber(),
                                uploadedPart.etag(),
                                uploadedPart.sizeBytes()
                        ))
                        .toList(),
                singleUploadUrlGrant == null ? null : singleUploadUrlGrant.uploadUrl(),
                singleUploadUrlGrant == null ? null : "PUT",
                singleUploadUrlGrant == null ? null : singleUploadUrlGrant.expiresInSeconds(),
                singleUploadUrlGrant == null ? null : Map.of("Content-Type", creationResult.uploadSession().contentType())
        );
    }

    @GetMapping("/{uploadSessionId}")
    public UploadSessionView getUploadSession(@PathVariable String uploadSessionId,
                                              @RequestHeader("X-App-Id") String tenantId,
                                              @RequestHeader("X-User-Id") String subjectId) {
        return toView(uploadAppService.getVisibleSession(tenantId, uploadSessionId, subjectId));
    }

    @GetMapping("/{uploadSessionId}/progress")
    public UploadProgressView getUploadProgress(@PathVariable String uploadSessionId,
                                                @RequestHeader("X-App-Id") String tenantId,
                                                @RequestHeader("X-User-Id") String subjectId) {
        return toProgressView(uploadAppService.getUploadProgress(tenantId, uploadSessionId, subjectId));
    }

    @PostMapping("/{uploadSessionId}/part-urls")
    public PartUploadUrlsView createPartUploadUrls(@PathVariable String uploadSessionId,
                                                   @RequestHeader("X-App-Id") String tenantId,
                                                   @RequestHeader("X-User-Id") String subjectId,
                                                   @Valid @RequestBody CreatePartUploadUrlsRequest request) {
        PartUploadUrlGrant grant = uploadAppService.issuePartUploadUrls(
                tenantId,
                uploadSessionId,
                subjectId,
                request.partNumbers(),
                fileCoreUploadProperties.getPartUrlTtl()
        );
        return new PartUploadUrlsView(
                grant.uploadSessionId(),
                grant.partUrls().stream()
                        .map(partUrl -> new PartUploadUrlView(
                                partUrl.partNumber(),
                                partUrl.uploadUrl(),
                                partUrl.expiresInSeconds()
                        ))
                        .toList()
        );
    }

    @PostMapping("/{uploadSessionId}/abort")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abortUploadSession(@PathVariable String uploadSessionId,
                                   @RequestHeader("X-App-Id") String tenantId,
                                   @RequestHeader("X-User-Id") String subjectId) {
        uploadAppService.abortSession(tenantId, uploadSessionId, subjectId);
    }

    @PostMapping("/{uploadSessionId}/complete")
    public UploadCompletionView completeUploadSession(@PathVariable String uploadSessionId,
                                                      @RequestHeader("X-App-Id") String tenantId,
                                                      @RequestHeader("X-User-Id") String subjectId,
                                                      @Valid @RequestBody CompleteUploadSessionRequest request) {
        UploadCompletion uploadCompletion = uploadAppService.completeSession(
                tenantId,
                uploadSessionId,
                subjectId,
                request.contentType(),
                request.parts() == null
                        ? List.of()
                        : request.parts().stream()
                        .map(part -> new CompletedUploadPart(part.partNumber(), part.etag()))
                        .toList()
        );
        return new UploadCompletionView(
                uploadCompletion.uploadSessionId(),
                uploadCompletion.fileId(),
                UploadSessionStatusView.valueOf(uploadCompletion.status().name())
        );
    }

    private UploadMode resolveUploadMode(CreateUploadSessionRequest request) {
        if (request.uploadMode() != UploadModeView.AUTO) {
            return UploadMode.valueOf(request.uploadMode().name());
        }
        return request.expectedSize() <= fileCoreUploadProperties.getAutoPresignedSingleMaxSizeBytes()
                ? UploadMode.PRESIGNED_SINGLE
                : UploadMode.DIRECT;
    }

    private UploadSessionView toView(UploadSession uploadSession) {
        return new UploadSessionView(
                uploadSession.uploadSessionId(),
                uploadSession.tenantId(),
                uploadSession.ownerId(),
                UploadModeView.valueOf(uploadSession.uploadMode().name()),
                AccessLevelView.valueOf(uploadSession.targetAccessLevel().name()),
                uploadSession.originalFilename(),
                uploadSession.contentType(),
                uploadSession.expectedSize(),
                uploadSession.fileHash(),
                uploadSession.chunkSizeBytes(),
                uploadSession.totalParts(),
                uploadSession.fileId(),
                UploadSessionStatusView.valueOf(uploadSession.status().name()),
                uploadSession.createdAt(),
                uploadSession.updatedAt(),
                uploadSession.expiresAt()
        );
    }

    private UploadProgressView toProgressView(UploadProgress uploadProgress) {
        return new UploadProgressView(
                uploadProgress.uploadSessionId(),
                uploadProgress.totalParts(),
                uploadProgress.completedParts(),
                uploadProgress.uploadedBytes(),
                uploadProgress.totalBytes(),
                uploadProgress.percentage(),
                uploadProgress.uploadedParts().stream()
                        .map(uploadedPart -> uploadedPart.partNumber())
                        .toList(),
                uploadProgress.uploadedParts().stream()
                        .map(uploadedPart -> new UploadedPartView(
                                uploadedPart.partNumber(),
                                uploadedPart.etag(),
                                uploadedPart.sizeBytes()
                        ))
                        .toList()
        );
    }

    private SingleUploadUrlGrant maybeIssueSingleUploadUrl(String tenantId,
                                                           String subjectId,
                                                           UploadSession uploadSession) {
        if (uploadSession.uploadMode() != UploadMode.PRESIGNED_SINGLE || uploadSession.hasCompletedFile()) {
            return null;
        }
        return uploadAppService.issueSingleUploadUrl(
                tenantId,
                uploadSession.uploadSessionId(),
                subjectId,
                fileCoreUploadProperties.getPartUrlTtl()
        );
    }
}
