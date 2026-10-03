
package com.paytm.seat_reservation.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shows")
public class Show {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_paise", nullable = false)
    private Long pricePaise;

    @Column(name = "total_seats", nullable = false)
    private Integer totalSeats;

    @Column(name = "per_user_limit", nullable = false)
    private Integer perUserLimit;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}