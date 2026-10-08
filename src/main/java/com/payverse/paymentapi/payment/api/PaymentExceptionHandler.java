package com.payverse.paymentapi.payment.api;

import com.payverse.paymentapi.idempotency.IdempotencyKeyInProgressException;
import com.payverse.paymentapi.idempotency.IdempotencyKeyReusedException;
import com.payverse.paymentapi.idempotency.InvalidIdempotencyKeyException;
import com.payverse.paymentapi.payment.application.PaymentNotFoundException;
import com.payverse.paymentapi.payment.domain.InvalidPaymentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice(basePackageClasses = PaymentController.class)
public class PaymentExceptionHandler {

    // Field names and messages only: rejected values can be personal or card data and are not echoed.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request body is invalid");
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableBody() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request body could not be read");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail missingHeader(MissingRequestHeaderException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "The " + exception.getHeaderName() + " header is required");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail invalidPathValue(MethodArgumentTypeMismatchException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getName() + " is invalid");
    }

    @ExceptionHandler({InvalidIdempotencyKeyException.class, InvalidPaymentException.class})
    ProblemDetail invalidRequest(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail paymentNotFound(PaymentNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail keyReused(IdempotencyKeyReusedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyInProgressException.class)
    ProblemDetail keyInProgress(IdempotencyKeyInProgressException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
