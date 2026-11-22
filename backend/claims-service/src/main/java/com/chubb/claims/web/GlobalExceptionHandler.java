package com.chubb.claims.web;

import com.chubb.claims.claim.AmountAboveCoverageLimitException;
import com.chubb.claims.claim.InvalidStatusTransitionException;
import com.chubb.claims.claim.NotAssignedOfficerException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    /** Reserve or approved amount above the policy's coverage limit. */
    @ExceptionHandler(AmountAboveCoverageLimitException.class)
    public ProblemDetail handleAboveLimit(AmountAboveCoverageLimitException ex) {
        return validationProblem(List.of(new FieldError("amount", ex.getMessage())));
    }

    /** The action doesn't fit the claim's current status (e.g. approving a claim that isn't in review). */
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ProblemDetail handleWrongStatus(InvalidStatusTransitionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** A staff member acted on a claim that isn't assigned to them. */
    @ExceptionHandler(NotAssignedOfficerException.class)
    public ProblemDetail handleNotAssigned(NotAssignedOfficerException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /** Someone else changed the same claim at the same moment (version check failed). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentChange(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This claim was just changed by someone else. Reload and try again.");
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
