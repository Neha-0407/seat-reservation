package com.paytm.seat_reservation.exception;

public class ReservationConflictException extends RuntimeException {

    private final ReservationDeclineReason reason;

    public ReservationConflictException(ReservationDeclineReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ReservationDeclineReason getReason() {
        return reason;
    }
}