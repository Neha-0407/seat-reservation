
package com.paytm.seat_reservation.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "reservation_seats")
public class ReservationSeat {

    @EmbeddedId
    private ReservationSeatId id;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "unit_price_paise", nullable = false)
    private Long unitPricePaise;

    public ReservationSeat() {
    }

    public ReservationSeatId getId() {
        return id;
    }

    public void setId(ReservationSeatId id) {
        this.id = id;
    }

    public UUID getShowId() {
        return showId;
    }

    public void setShowId(UUID showId) {
        this.showId = showId;
    }

    public Long getUnitPricePaise() {
        return unitPricePaise;
    }

    public void setUnitPricePaise(Long unitPricePaise) {
        this.unitPricePaise = unitPricePaise;
    }
}