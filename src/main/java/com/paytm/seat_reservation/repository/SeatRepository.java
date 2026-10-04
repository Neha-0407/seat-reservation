package com.paytm.seat_reservation.repository;

import com.paytm.seat_reservation.entity.Seat;
import com.paytm.seat_reservation.entity.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByShowId(UUID showId);

    List<Seat> findByShowIdAndStatus(UUID showId, SeatStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select seat from Seat seat where seat.showId = :showId and seat.id in :seatIds order by seat.id")
    List<Seat> findSeatsForUpdate(@Param("showId") UUID showId, @Param("seatIds") List<UUID> seatIds);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select seat from Seat seat where seat.showId = :showId and seat.seatNumber in :seatNumbers order by seat.id")
        List<Seat> findSeatsForUpdateByNumbers(
            @Param("showId") UUID showId,
            @Param("seatNumbers") List<String> seatNumbers
        );

    Optional<Seat> findByShowIdAndSeatNumber(UUID showId, String seatNumber);

    @Query(value = "SELECT s.id, COUNT(seat.id) FROM shows s LEFT JOIN seats seat ON seat.show_id = s.id AND seat.status = 'AVAILABLE' GROUP BY s.id", nativeQuery = true)
    List<Object[]> countAvailableSeatsByShow();
}
