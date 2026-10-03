package com.paytm.seat_reservation.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "seat_categories")
public class SeatCategory {

    @Id
    private UUID id;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "category_name", nullable = false, length = 50)
    private String categoryName;

    @Column(name = "price_paise", nullable = false)
    private Long pricePaise;

    public SeatCategory() {
    }

    // Generate getters and setters using your IDE.
}