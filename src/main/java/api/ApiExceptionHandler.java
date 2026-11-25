package api;

import app.IdempotencyException;
import domain.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import store.ConcurrencyException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(ConcurrencyException.class)
    public ProblemDetail handleConcurrency(ConcurrencyException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The account was modified concurrently, please retry");
    }

    @ExceptionHandler(IdempotencyException.class)
    public ProblemDetail handleIdempotency(IdempotencyException e) {
        HttpStatus status = e.isInProgress() ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
        return ProblemDetail.forStatusAndDetail(status, e.getMessage());
    }
}
