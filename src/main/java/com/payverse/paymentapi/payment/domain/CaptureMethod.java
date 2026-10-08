package com.payverse.paymentapi.payment.domain;

public enum CaptureMethod {
    /** Authorize now, capture later. */
    MANUAL,
    /** Authorize and capture in one call (a sale). */
    AUTOMATIC
}
