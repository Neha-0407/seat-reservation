package com.paytm.seat_reservation.dto;

import java.util.List;
import java.util.UUID;

public class ReservationResponse {

    private UUID reservationId;
    private UUID showId;
    private String userId;
    private String status;
    private Long amountPaise;
    private List<String> seats;

    public ReservationResponse() {
    }

    public ReservationResponse(
            UUID reservationId,
            UUID showId,
            String userId,
            String status,
            Long amountPaise,
            List<String> seats
    ) {
        this.reservationId = reservationId;
        this.showId = showId;
        this.userId = userId;
        this.status = status;
        this.amountPaise = amountPaise;
        this.seats = seats;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public UUID getShowId() {
        return showId;
    }

    public void setShowId(UUID showId) {
        this.showId = showId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAmountPaise() {
        return amountPaise;
    }

    public void setAmountPaise(Long amountPaise) {
        this.amountPaise = amountPaise;
    }

    public List<String> getSeats() {
        return seats;
    }

    public void setSeats(List<String> seats) {
        this.seats = seats;
    }
}
