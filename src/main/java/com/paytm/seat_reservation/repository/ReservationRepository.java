package com.paytm.seat_reservation.repository;

import com.paytm.seat_reservation.entity.Reservation;
import com.paytm.seat_reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByShowIdAndUserIdAndIdempotencyKey(UUID showId, String userId, String idempotencyKey);

    List<Reservation> findByUserIdAndShowId(String userId, UUID showId);

    List<Reservation> findByShowIdAndStatus(UUID showId, ReservationStatus status);
}
