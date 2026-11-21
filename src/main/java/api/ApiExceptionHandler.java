package api;

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
}
