package com.paytm.seat_reservation.repository;

import com.paytm.seat_reservation.entity.ReservationSeat;
import com.paytm.seat_reservation.entity.ReservationSeatId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, ReservationSeatId> {

    List<ReservationSeat> findByIdReservationId(UUID reservationId);

    List<ReservationSeat> findByIdSeatId(UUID seatId);

    List<ReservationSeat> findByShowId(UUID showId);
}
