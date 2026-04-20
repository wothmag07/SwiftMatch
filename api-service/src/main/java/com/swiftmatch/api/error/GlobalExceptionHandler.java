package com.swiftmatch.api.error;

import com.swiftmatch.common.error.ProblemType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Produces RFC 7807 Problem+JSON responses for application errors.
 * MVP-binding types (per Amendment 001 §4.4): validation, not-found, no-driver-found.
 * Extended types (driver-on-trip) also flow through here for consistency.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(ProblemType.VALIDATION.uri());
        problem.setTitle("Validation failed");
        problem.setDetail(String.join("; ", fieldErrors));
        problem.setProperty("fieldErrors", fieldErrors);
        return body(problem);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(ProblemType.NOT_FOUND.uri());
        problem.setTitle("Resource not found");
        problem.setDetail(ex.getMessage());
        return body(problem);
    }

    @ExceptionHandler(DriverOnTripException.class)
    public ResponseEntity<ProblemDetail> handleDriverOnTrip(DriverOnTripException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(ProblemType.DRIVER_ON_TRIP.uri());
        problem.setTitle("Driver is on a trip");
        problem.setDetail(ex.getMessage());
        problem.setProperty("driverId", ex.getDriverId().toString());
        return body(problem);
    }

    private static ResponseEntity<ProblemDetail> body(ProblemDetail problem) {
        return ResponseEntity
                .status(problem.getStatus())
                .header("Content-Type", "application/problem+json")
                .body(problem);
    }
}
