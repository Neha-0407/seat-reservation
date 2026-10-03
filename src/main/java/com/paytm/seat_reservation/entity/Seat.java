package com.paytm.seat_reservation.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seats")
public class Seat {

    @Id
    private UUID id;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(name = "reserved_by")
    private UUID reservedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Seat() {
    }

    // Generate getters and setters using your IDE.
}