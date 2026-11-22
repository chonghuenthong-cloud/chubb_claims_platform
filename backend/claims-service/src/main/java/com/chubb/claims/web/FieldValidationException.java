package com.chubb.claims.web;

/**
 * A business rule failed for one request field (e.g. incident date outside the policy period).
 * Returned as 400 in the same shape as annotation-based validation errors, so the UI handles both the same way.
 */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
