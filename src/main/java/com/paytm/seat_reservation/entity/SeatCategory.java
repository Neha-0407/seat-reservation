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

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getShowId() {
        return showId;
    }

    public void setShowId(UUID showId) {
        this.showId = showId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(Long pricePaise) {
        this.pricePaise = pricePaise;
    }
}