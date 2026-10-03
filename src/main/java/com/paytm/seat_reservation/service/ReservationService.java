package com.paytm.seat_reservation.service;

import com.paytm.seat_reservation.dto.ReservationResponse;
import com.paytm.seat_reservation.dto.ReserveRequest;
import com.paytm.seat_reservation.entity.Reservation;
import com.paytm.seat_reservation.entity.ReservationSeat;
import com.paytm.seat_reservation.entity.ReservationSeatId;
import com.paytm.seat_reservation.entity.ReservationStatus;
import com.paytm.seat_reservation.entity.Seat;
import com.paytm.seat_reservation.entity.SeatCategory;
import com.paytm.seat_reservation.entity.SeatStatus;
import com.paytm.seat_reservation.entity.Show;
import com.paytm.seat_reservation.repository.ReservationRepository;
import com.paytm.seat_reservation.repository.ReservationSeatRepository;
import com.paytm.seat_reservation.repository.SeatRepository;
import com.paytm.seat_reservation.repository.SeatCategoryRepository;
import com.paytm.seat_reservation.repository.ShowRepository;
import com.paytm.seat_reservation.repository.UserShowLockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final SeatRepository seatRepository;
    private final SeatCategoryRepository seatCategoryRepository;
    private final ShowRepository showRepository;
    private final UserShowLockRepository userShowLockRepository;

    public ReservationService(
            ReservationRepository reservationRepository,
            ReservationSeatRepository reservationSeatRepository,
            SeatRepository seatRepository,
            SeatCategoryRepository seatCategoryRepository,
            ShowRepository showRepository,
            UserShowLockRepository userShowLockRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationSeatRepository = reservationSeatRepository;
        this.seatRepository = seatRepository;
        this.seatCategoryRepository = seatCategoryRepository;
        this.showRepository = showRepository;
        this.userShowLockRepository = userShowLockRepository;
    }

    @Transactional
    public ReservationResponse reserveSeats(String userId, ReserveRequest request) {
        Show show = showRepository.findById(request.getShowId())
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + request.getShowId()));

        if (request.getSeatIds() == null || request.getSeatIds().isEmpty()) {
            throw new IllegalArgumentException("At least one seat must be selected");
        }

        if (request.getSeatIds().size() > show.getPerUserLimit()) {
            throw new IllegalArgumentException("Seat selection exceeds per-user limit");
        }

        if (reservationRepository.findByShowIdAndUserIdAndIdempotencyKey(
                request.getShowId(), userId, request.getIdempotencyKey()).isPresent()) {
            throw new IllegalStateException("Duplicate reservation request");
        }

        List<Seat> seats = new ArrayList<>();
        Map<UUID, SeatCategory> categoriesById = new HashMap<>();
        for (SeatCategory category : seatCategoryRepository.findByShowId(show.getId())) {
            categoriesById.put(category.getId(), category);
        }

        for (UUID seatId : request.getSeatIds()) {
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId));
            if (!seat.getShowId().equals(show.getId())) {
                throw new IllegalArgumentException("Seat does not belong to this show");
            }
            if (!categoriesById.containsKey(seat.getCategoryId())) {
                throw new IllegalStateException("Seat category not found for seat: " + seatId);
            }
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                throw new IllegalStateException("Seat is not available: " + seatId);
            }
            seats.add(seat);
        }

        long totalAmount = 0L;
        for (Seat seat : seats) {
            totalAmount += categoriesById.get(seat.getCategoryId()).getPricePaise();
            seat.setStatus(SeatStatus.CONFIRMED);
            seatRepository.save(seat);
        }

        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setShowId(show.getId());
        reservation.setUserId(userId);
        reservation.setIdempotencyKey(request.getIdempotencyKey());
        reservation.setRequestHash(String.valueOf(request.getSeatIds().hashCode()));
        reservation.setAmountPaise(totalAmount);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setCreatedAt(OffsetDateTime.now());
        reservation = reservationRepository.save(reservation);

        for (Seat seat : seats) {
            seat.setReservedBy(reservation.getId());
            seat.setStatus(SeatStatus.CONFIRMED);
            seatRepository.save(seat);

            ReservationSeat reservationSeat = new ReservationSeat();
            reservationSeat.setId(new ReservationSeatId(reservation.getId(), seat.getId()));
            reservationSeat.setShowId(show.getId());
            reservationSeat.setUnitPricePaise(categoriesById.get(seat.getCategoryId()).getPricePaise());
            reservationSeatRepository.save(reservationSeat);
        }

        return new ReservationResponse(reservation.getId(), reservation.getStatus().name(), totalAmount, request.getSeatIds());
    }
}
