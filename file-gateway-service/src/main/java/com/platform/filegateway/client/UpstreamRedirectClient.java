package com.platform.filegateway.client;

import com.platform.filegateway.domain.GatewayAccessIdentity;
import com.platform.filegateway.domain.GatewayRedirectResponse;

public interface UpstreamRedirectClient {

    GatewayRedirectResponse resolveContentRedirect(String fileId, GatewayAccessIdentity identity);
}
