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
import com.paytm.seat_reservation.exception.ReservationConflictException;
import com.paytm.seat_reservation.exception.ReservationDeclineReason;
import com.paytm.seat_reservation.repository.ReservationRepository;
import com.paytm.seat_reservation.repository.ReservationSeatRepository;
import com.paytm.seat_reservation.repository.SeatRepository;
import com.paytm.seat_reservation.repository.SeatCategoryRepository;
import com.paytm.seat_reservation.repository.ShowRepository;
import com.paytm.seat_reservation.repository.UserShowLockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReservationService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationService.class);

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
    public ReservationResponse reserveSeats(UUID showId, String userId, ReserveRequest request) {
        if (request.getSeats() == null || request.getSeats().isEmpty()) {
            throw new IllegalArgumentException("At least one seat must be selected");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }

        Show show = showRepository.findById(showId)
            .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));

        List<String> sortedSeatNumbers = request.getSeats().stream().sorted().toList();
        if (new HashSet<>(sortedSeatNumbers).size() != sortedSeatNumbers.size()) {
            throw new ReservationConflictException(
                    ReservationDeclineReason.DUPLICATE_SEAT,
                    "A seat may only be requested once");
        }
        String requestHash = sortedSeatNumbers.stream()
            .map(seatNumber -> seatNumber.length() + ":" + seatNumber)
            .collect(Collectors.joining());

        lockUserShow(show.getId(), userId);

        var existingReservation = reservationRepository.findByShowIdAndUserIdAndIdempotencyKey(
                show.getId(), userId, request.getIdempotencyKey());
        if (existingReservation.isPresent()) {
            Reservation existing = existingReservation.get();
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new ReservationConflictException(
                    ReservationDeclineReason.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency key was already used with different seats");
            }
                logger.atDebug()
                    .addKeyValue("event", "reservation_idempotency_replay")
                    .addKeyValue("request_id", MDC.get("request_id"))
                    .addKeyValue("show_id", show.getId())
                    .addKeyValue("reservation_id", existing.getId())
                    .log("reservation_idempotency_replay");
            return toResponseForSeatIds(existing, reservationSeatRepository.findByIdReservationId(existing.getId()).stream()
                    .map(reservationSeat -> reservationSeat.getId().getSeatId())
                    .toList());
        }

        long alreadyReserved = reservationSeatRepository.countConfirmedSeatsForUserAndShow(show.getId(), userId);
        if (alreadyReserved + sortedSeatNumbers.size() > show.getPerUserLimit()) {
            throw new ReservationConflictException(
                    ReservationDeclineReason.PER_USER_LIMIT,
                    "Seat selection exceeds per-user limit");
        }

        List<Seat> seats = seatRepository.findSeatsForUpdateByNumbers(show.getId(), sortedSeatNumbers);
        if (seats.size() != sortedSeatNumbers.size()) {
            throw new IllegalArgumentException("One or more seats do not belong to this show");
        }

        Map<UUID, SeatCategory> categoriesById = new HashMap<>();
        for (SeatCategory category : seatCategoryRepository.findByShowId(show.getId())) {
            categoriesById.put(category.getId(), category);
        }

        for (Seat seat : seats) {
            if (!categoriesById.containsKey(seat.getCategoryId())) {
                throw new IllegalStateException("Seat category not found for seat: " + seat.getId());
            }
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                throw new ReservationConflictException(
                        ReservationDeclineReason.SEAT_TAKEN,
                        "Seat is already taken: " + seat.getId());
            }
        }

        long totalAmount = 0L;
        for (Seat seat : seats) {
            totalAmount += categoriesById.get(seat.getCategoryId()).getPricePaise();
        }

        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setShowId(show.getId());
        reservation.setUserId(userId);
        reservation.setIdempotencyKey(request.getIdempotencyKey());
        reservation.setRequestHash(requestHash);
        reservation.setAmountPaise(totalAmount);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setCreatedAt(OffsetDateTime.now());
        reservation = reservationRepository.save(reservation);

        List<ReservationSeat> reservationSeats = new ArrayList<>();
        for (Seat seat : seats) {
            seat.setReservedBy(reservation.getId());
            seat.setStatus(SeatStatus.CONFIRMED);

            ReservationSeat reservationSeat = new ReservationSeat();
            reservationSeat.setId(new ReservationSeatId(reservation.getId(), seat.getId()));
            reservationSeat.setShowId(show.getId());
            reservationSeat.setUnitPricePaise(categoriesById.get(seat.getCategoryId()).getPricePaise());
            reservationSeats.add(reservationSeat);
        }
        seatRepository.saveAll(seats);
        reservationSeatRepository.saveAll(reservationSeats);

        return toResponse(reservation, sortedSeatNumbers);
    }

    @Transactional
    public ReservationResponse cancelReservation(String userId, UUID reservationId) {
        Reservation snapshot = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));
        if (!snapshot.getUserId().equals(userId)) {
            throw new AccessDeniedException("Reservation belongs to another user");
        }

        lockUserShow(snapshot.getShowId(), userId);
        Reservation reservation = reservationRepository.findForUpdateById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));
        if (!reservation.getUserId().equals(userId)) {
            throw new AccessDeniedException("Reservation belongs to another user");
        }

        List<UUID> seatIds = reservationSeatRepository.findByIdReservationId(reservationId).stream()
                .map(reservationSeat -> reservationSeat.getId().getSeatId())
                .sorted()
                .toList();
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return toResponseForSeatIds(reservation, seatIds);
        }

        List<Seat> seats = seatRepository.findSeatsForUpdate(reservation.getShowId(), seatIds);
        if (seats.size() != seatIds.size()) {
            throw new ReservationConflictException(
                    ReservationDeclineReason.RESERVATION_INCONSISTENT,
                    "Reservation seat records are inconsistent");
        }
        for (Seat seat : seats) {
            if (!reservationId.equals(seat.getReservedBy()) || seat.getStatus() != SeatStatus.CONFIRMED) {
                throw new ReservationConflictException(
                        ReservationDeclineReason.RESERVATION_INCONSISTENT,
                        "Reservation no longer owns all of its seats");
            }
            seat.setReservedBy(null);
            seat.setStatus(SeatStatus.AVAILABLE);
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(OffsetDateTime.now());
        seatRepository.saveAll(seats);
        reservationRepository.save(reservation);
        return toResponseForSeatIds(reservation, seatIds);
    }

    private void lockUserShow(UUID showId, String userId) {
        userShowLockRepository.createLockIfMissing(showId, userId);
        userShowLockRepository.findForUpdateByShowIdAndUserId(showId, userId)
                .orElseThrow(() -> new IllegalStateException("Could not acquire user/show lock"));
    }

    private ReservationResponse toResponseForSeatIds(Reservation reservation, List<UUID> seatIds) {
        List<String> seatNumbers = seatRepository.findAllById(seatIds).stream()
                .map(Seat::getSeatNumber)
                .sorted()
                .toList();
        return toResponse(reservation, seatNumbers);
    }

    private ReservationResponse toResponse(Reservation reservation, List<String> seatNumbers) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getShowId(),
                reservation.getUserId(),
                reservation.getStatus().name().toLowerCase(),
                reservation.getAmountPaise(),
                seatNumbers
        );
    }
}
