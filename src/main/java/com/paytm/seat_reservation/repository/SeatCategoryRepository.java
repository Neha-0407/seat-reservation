package com.paytm.seat_reservation.repository;

import com.paytm.seat_reservation.entity.SeatCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatCategoryRepository extends JpaRepository<SeatCategory, UUID> {

    List<SeatCategory> findByShowId(UUID showId);

    Optional<SeatCategory> findByShowIdAndCategoryName(UUID showId, String categoryName);
}
