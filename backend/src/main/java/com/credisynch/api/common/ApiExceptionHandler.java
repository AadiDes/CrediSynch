package com.credisynch.api.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * RFC 7807 problem responses. Validation failures list the offending fields;
 * unexpected failures never leak stack traces or internal messages to the caller.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidationFailure(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://credisynch.dev/problems/validation-failed"));
        problem.setTitle("Request validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail onHandlerMethodValidationFailure(HandlerMethodValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://credisynch.dev/problems/validation-failed"));
        problem.setTitle("Request validation failed");
        problem.setProperty("errors", List.of(ex.getMessage()));
        return problem;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail onMissingRequestHeader(MissingRequestHeaderException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://credisynch.dev/problems/validation-failed"));
        problem.setTitle("Required header missing");
        problem.setDetail("Required request header '%s' is missing.".formatted(ex.getHeaderName()));
        return problem;
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ProblemDetail onAuthorizationDenied(AuthorizationDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create("https://credisynch.dev/problems/forbidden"));
        problem.setTitle("Forbidden");
        problem.setDetail("You do not have permission to access this resource.");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://credisynch.dev/problems/invalid-request"));
        problem.setTitle("Invalid request");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail onUnexpected(Exception ex, HttpServletRequest request) {
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class)
                .error("Unhandled failure on {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("https://credisynch.dev/problems/internal-error"));
        problem.setTitle("Internal error");
        problem.setDetail("The request could not be completed. Quote the X-Correlation-Id header when reporting this.");
        return problem;
    }
}
