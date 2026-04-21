package com.swiftmatch.api.error;

import com.swiftmatch.api.ride.NoDriverFoundException;
import com.swiftmatch.api.ride.OutOfServiceAreaException;
import com.swiftmatch.api.ride.RiderHasActiveRideException;
import com.swiftmatch.common.error.ProblemType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Produces RFC 7807 Problem+JSON responses for application errors.
 * MVP-binding types: validation, not-found, no-driver-found.
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMalformedJson(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(ProblemType.VALIDATION.uri());
        problem.setTitle("Malformed request body");
        problem.setDetail(ex.getMostSpecificCause().getMessage());
        return body(problem);
    }

    @ExceptionHandler(IngestionTimeoutException.class)
    public ResponseEntity<ProblemDetail> handleIngestionTimeout(IngestionTimeoutException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(ProblemType.INGESTION_TIMEOUT.uri());
        problem.setTitle("Ingestion publisher timed out");
        problem.setDetail(ex.getMessage());
        problem.setProperty("driverId", ex.getDriverId().toString());
        return body(problem);
    }

    @ExceptionHandler(NoDriverFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoDriverFound(NoDriverFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(ProblemType.NO_DRIVER_FOUND.uri());
        problem.setTitle("No driver available");
        problem.setDetail(ex.getMessage());
        problem.setProperty("rideId", ex.getRideId().toString());
        return body(problem);
    }

    @ExceptionHandler(OutOfServiceAreaException.class)
    public ResponseEntity<ProblemDetail> handleOutOfServiceArea(OutOfServiceAreaException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(ProblemType.OUT_OF_SERVICE_AREA.uri());
        problem.setTitle("Coordinates outside service area");
        problem.setDetail(ex.getMessage());
        problem.setProperty("field", ex.getField());
        return body(problem);
    }

    @ExceptionHandler(RiderHasActiveRideException.class)
    public ResponseEntity<ProblemDetail> handleRiderHasActiveRide(RiderHasActiveRideException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(ProblemType.RIDER_HAS_ACTIVE_RIDE.uri());
        problem.setTitle("Rider already has an active ride");
        problem.setDetail(ex.getMessage());
        problem.setProperty("riderId", ex.getRiderId().toString());
        return body(problem);
    }

    private static ResponseEntity<ProblemDetail> body(ProblemDetail problem) {
        return ResponseEntity
                .status(problem.getStatus())
                .header("Content-Type", "application/problem+json")
                .body(problem);
    }
}
