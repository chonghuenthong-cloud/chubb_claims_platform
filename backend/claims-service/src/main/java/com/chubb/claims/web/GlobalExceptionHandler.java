package com.chubb.claims.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Turns exceptions into RFC 9457 Problem Details responses.
 * Field-level errors carry an "errors" list of {field, message}, which the UI shows next to each form field.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** One invalid field, as sent to the client. */
    public record FieldError(String field, String message) {
    }

    /** Annotation-based validation failed (@NotNull, @Size, ...). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidBody(MethodArgumentNotValidException ex) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return validationProblem(errors);
    }

    /** A business rule failed for one field. */
    @ExceptionHandler(FieldValidationException.class)
    public ProblemDetail handleFieldRule(FieldValidationException ex) {
        return validationProblem(List.of(new FieldError(ex.getField(), ex.getMessage())));
    }

    /** Malformed JSON, or a value of the wrong type (e.g. an unparseable date). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    }

    private ProblemDetail validationProblem(List<FieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }
}
