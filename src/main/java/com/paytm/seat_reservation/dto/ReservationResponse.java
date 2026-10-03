package com.paytm.seat_reservation.dto;

import java.util.List;
import java.util.UUID;

public class ReservationResponse {

    private UUID reservationId;
    private String status;
    private Long amountPaise;
    private List<UUID> seatIds;

    public ReservationResponse() {
    }

    public ReservationResponse(UUID reservationId, String status, Long amountPaise, List<UUID> seatIds) {
        this.reservationId = reservationId;
        this.status = status;
        this.amountPaise = amountPaise;
        this.seatIds = seatIds;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
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

    public List<UUID> getSeatIds() {
        return seatIds;
    }

    public void setSeatIds(List<UUID> seatIds) {
        this.seatIds = seatIds;
    }
}
