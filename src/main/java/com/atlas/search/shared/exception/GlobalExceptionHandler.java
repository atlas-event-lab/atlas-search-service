package com.atlas.search.shared.exception;

import com.atlas.search.search.exception.SearchValidationException;
import com.atlas.search.shared.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates all exceptions into RFC 7807 Problem Details responses (API-005).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldErrorDetail(e.getField(), e.getDefaultMessage()))
                .toList();

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                ProblemTypes.VALIDATION,
                "Validation Error",
                request);
        problem.setProperty("errors", errors);
        return respond(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(SearchValidationException.class)
    public ResponseEntity<ProblemDetail> handleSearchValidation(
            SearchValidationException ex, HttpServletRequest request) {

        ProblemDetail problem =
                problem(HttpStatus.BAD_REQUEST, ex.getMessage(), ProblemTypes.VALIDATION, "Validation Error", request);
        if (ex.getErrors() != null && !ex.getErrors().isEmpty()) {
            problem.setProperty("errors", ex.getErrors());
        }
        return respond(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing",
                ProblemTypes.VALIDATION,
                "Validation Error",
                request);
        return respond(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Invalid value for '" + ex.getName() + "'",
                ProblemTypes.VALIDATION,
                "Validation Error",
                request);
        return respond(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST, "Malformed request body", ProblemTypes.VALIDATION, "Validation Error", request);
        return respond(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {

        ProblemDetail problem =
                problem(HttpStatus.NOT_FOUND, "Resource not found", ProblemTypes.NOT_FOUND, "Not Found", request);
        return respond(HttpStatus.NOT_FOUND, problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error processing request to {}", request.getRequestURI(), ex);
        ProblemDetail problem = problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                ProblemTypes.INTERNAL_ERROR,
                "Internal Server Error",
                request);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, problem);
    }

    private ProblemDetail problem(
            HttpStatus status, String detail, URI type, String title, HttpServletRequest request) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setType(type);
        p.setTitle(title);
        p.setInstance(URI.create(request.getRequestURI()));
        p.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return p;
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatus status, ProblemDetail problem) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
