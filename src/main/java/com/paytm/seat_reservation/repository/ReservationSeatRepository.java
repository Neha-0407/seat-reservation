package com.paytm.seat_reservation.repository;

import com.paytm.seat_reservation.entity.ReservationSeat;
import com.paytm.seat_reservation.entity.ReservationSeatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, ReservationSeatId> {

    List<ReservationSeat> findByIdReservationId(UUID reservationId);

    List<ReservationSeat> findByIdSeatId(UUID seatId);

    List<ReservationSeat> findByShowId(UUID showId);

    @Query(value = "SELECT COUNT(*) FROM reservation_seats rs JOIN reservations r ON r.id = rs.reservation_id WHERE r.show_id = :showId AND r.user_id = :userId AND r.status = 'CONFIRMED'", nativeQuery = true)
    long countConfirmedSeatsForUserAndShow(@Param("showId") UUID showId, @Param("userId") String userId);
}
