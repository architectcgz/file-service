package com.platform.filegateway.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class GatewayException extends RuntimeException {

    private final HttpStatusCode status;

    public GatewayException(HttpStatusCode status, String message) {
        super(message);
        this.status = status;
    }

    public GatewayException(HttpStatusCode status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
