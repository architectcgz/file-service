package com.platform.fileservice.contract.access.model;

import java.util.List;

/**
 * Batch ticket issuing response.
 */
public record BatchAccessTicketView(
        List<BatchAccessTicketItemView> items
) {
}
