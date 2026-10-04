package com.paytm.seat_reservation.exception;

public enum ReservationDeclineReason {
    DUPLICATE_SEAT("duplicate-seat"),
    IDEMPOTENCY_KEY_REUSED("idempotency-key-reused"),
    PER_USER_LIMIT("per-user-limit"),
    SEAT_TAKEN("seat-taken"),
    RESERVATION_INCONSISTENT("reservation-inconsistent");

    private final String code;

    ReservationDeclineReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}