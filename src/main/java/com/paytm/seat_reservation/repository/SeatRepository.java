package com.paytm.seat_reservation.repository;

import com.paytm.seat_reservation.entity.Seat;
import com.paytm.seat_reservation.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByShowId(UUID showId);

    List<Seat> findByShowIdAndStatus(UUID showId, SeatStatus status);

    Optional<Seat> findByShowIdAndSeatNumber(UUID showId, String seatNumber);
}
