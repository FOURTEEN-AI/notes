package de.fourteen.notes.web;

import de.fourteen.notes.domain.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps the failures of the inner rings to HTTP statuses. */
@RestControllerAdvice
class DomainExceptionHandler {

    @ExceptionHandler
    ResponseEntity<ProblemDetail> handle(DomainException e) {
        HttpStatus status =
                switch (e.kind()) {
                    case INVALID -> HttpStatus.BAD_REQUEST;
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case CONFLICT -> HttpStatus.CONFLICT;
                    case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                    case UPSTREAM_FAILED -> HttpStatus.BAD_GATEWAY;
                };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, e.getMessage()));
    }
}
