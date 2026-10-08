package com.payverse.paymentapi.threeds.api;

import com.payverse.paymentapi.payment.application.PaymentNotFoundException;
import com.payverse.paymentapi.payment.domain.PaymentInvalidStateException;
import com.payverse.paymentapi.threeds.application.ThreeDSInvalidStateException;
import com.payverse.paymentapi.threeds.application.ThreeDSProviderException;
import com.payverse.paymentapi.threeds.application.ThreeDSSessionNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = ThreeDSController.class)
public class ThreeDSExceptionHandler {

    @ExceptionHandler(ThreeDSSessionNotFoundException.class)
    ProblemDetail sessionNotFound(ThreeDSSessionNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail paymentNotFound(PaymentNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({ThreeDSInvalidStateException.class, PaymentInvalidStateException.class})
    ProblemDetail invalidState(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate() {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The payment or its 3DS session was modified by another request");
    }

    @ExceptionHandler(ThreeDSProviderException.class)
    ProblemDetail providerFailure() {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, "The 3DS provider could not process the request");
    }
}
