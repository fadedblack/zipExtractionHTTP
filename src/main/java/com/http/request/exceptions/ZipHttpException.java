package com.http.request.exceptions;

/**
 * Exception thrown when HTTP operations fail during ZIP file processing.
 * This exception is specifically for HTTP-related errors (connection failures,
 * invalid status codes, timeout issues, etc.) as opposed to ZIP parsing errors.
 */
public class ZipHttpException extends Exception {

    /**
     * Constructs a new ZipHttpException with the specified detail message.
     *
     * @param message the detail message explaining the HTTP error
     */
    public ZipHttpException(String message) {
        super(message);
    }

    /**
     * Constructs a new ZipHttpException with the specified detail message and cause.
     *
     * @param message the detail message explaining the HTTP error
     * @param cause the cause of the HTTP error (typically IOException or InterruptedException)
     */
    public ZipHttpException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new ZipHttpException with the specified cause.
     * The detail message is set to the cause's string representation.
     *
     * @param cause the cause of the HTTP error
     */
    public ZipHttpException(Throwable cause) {
        super(cause);
    }
}
