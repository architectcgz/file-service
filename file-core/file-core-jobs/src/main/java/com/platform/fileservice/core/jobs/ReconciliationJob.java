package com.platform.fileservice.core.jobs;

import com.platform.fileservice.core.application.service.CleanupAppService;

/**
 * Placeholder job entry for future reconciliation and cleanup scheduling.
 */
public final class ReconciliationJob {

    private final CleanupAppService cleanupAppService;

    public ReconciliationJob(CleanupAppService cleanupAppService) {
        this.cleanupAppService = cleanupAppService;
    }

    public int runOnce() {
        return cleanupAppService.expireUploadSessions();
    }
}
