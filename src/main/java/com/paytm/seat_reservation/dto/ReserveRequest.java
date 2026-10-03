package com.paytm.seat_reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class ReserveRequest {

    @NotNull
    private UUID showId;

    @NotBlank
    private String idempotencyKey;

    @NotNull
    private List<UUID> seatIds;

    public ReserveRequest() {
    }

    public UUID getShowId() {
        return showId;
    }

    public void setShowId(UUID showId) {
        this.showId = showId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public List<UUID> getSeatIds() {
        return seatIds;
    }

    public void setSeatIds(List<UUID> seatIds) {
        this.seatIds = seatIds;
    }
}
