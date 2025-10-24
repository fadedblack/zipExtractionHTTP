package com.http.request.exceptions;

public class ZipHttpException extends Exception {
    public ZipHttpException(String message) {
        super(message);
    }
    public ZipHttpException(String message, Throwable cause) {
        super(message, cause);
    }
}
