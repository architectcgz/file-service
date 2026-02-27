package com.platform.fileservice.client.exception;

/**
 * Exception thrown when JSON parsing or response deserialization fails.
 * Indicates that the server response could not be parsed into the expected format.
 */
public class ParseException extends FileServiceException {

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
