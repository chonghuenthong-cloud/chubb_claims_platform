package com.chubb.claims.web;

import org.springframework.http.HttpStatus;

/** An error that maps directly to an HTTP status, e.g. 401, 403 or 404. Rendered by {@link GlobalExceptionHandler}. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
