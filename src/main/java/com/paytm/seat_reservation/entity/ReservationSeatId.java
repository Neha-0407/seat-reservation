package com.paytm.seat_reservation.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ReservationSeatId implements Serializable {

    private UUID reservationId;
    private UUID seatId;

    public ReservationSeatId() {
    }

    public ReservationSeatId(UUID reservationId, UUID seatId) {
        this.reservationId = reservationId;
        this.seatId = seatId;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public void setReservationId(UUID reservationId) {
        this.reservationId = reservationId;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public void setSeatId(UUID seatId) {
        this.seatId = seatId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ReservationSeatId other)) return false;
        return Objects.equals(reservationId, other.reservationId)
                && Objects.equals(seatId, other.seatId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId, seatId);
    }
}