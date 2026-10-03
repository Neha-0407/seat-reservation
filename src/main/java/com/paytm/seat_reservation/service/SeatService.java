package com.paytm.seat_reservation.service;

import com.paytm.seat_reservation.dto.CreateSeatsRequest;
import com.paytm.seat_reservation.entity.Seat;
import com.paytm.seat_reservation.entity.SeatCategory;
import com.paytm.seat_reservation.entity.SeatStatus;
import com.paytm.seat_reservation.entity.Show;
import com.paytm.seat_reservation.repository.SeatCategoryRepository;
import com.paytm.seat_reservation.repository.SeatRepository;
import com.paytm.seat_reservation.repository.ShowAdminRepository;
import com.paytm.seat_reservation.repository.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SeatService {

    private final SeatRepository seatRepository;
    private final SeatCategoryRepository seatCategoryRepository;
    private final ShowRepository showRepository;
    private final ShowAdminRepository showAdminRepository;

    public SeatService(
            SeatRepository seatRepository,
            SeatCategoryRepository seatCategoryRepository,
            ShowRepository showRepository,
            ShowAdminRepository showAdminRepository
    ) {
        this.seatRepository = seatRepository;
        this.seatCategoryRepository = seatCategoryRepository;
        this.showRepository = showRepository;
        this.showAdminRepository = showAdminRepository;
    }

    @Transactional
    public List<Seat> createSeats(UUID showId, String userId, CreateSeatsRequest request) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));
        requireShowAdmin(showId, userId);
        SeatCategory category = seatCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Seat category not found: " + request.getCategoryId()));
        if (!category.getShowId().equals(showId)) {
            throw new IllegalArgumentException("Seat category does not belong to this show");
        }

        List<Seat> existingSeats = seatRepository.findByShowId(showId);
        if (existingSeats.size() + request.getSeatNumbers().size() > show.getTotalSeats()) {
            throw new IllegalArgumentException("Seat count exceeds show capacity");
        }

        Set<String> requestedNumbers = new HashSet<>(request.getSeatNumbers());
        if (requestedNumbers.size() != request.getSeatNumbers().size()) {
            throw new IllegalArgumentException("Seat numbers must be unique");
        }
        Set<String> existingNumbers = new HashSet<>();
        for (Seat seat : existingSeats) {
            existingNumbers.add(seat.getSeatNumber());
        }
        if (requestedNumbers.stream().anyMatch(existingNumbers::contains)) {
            throw new IllegalArgumentException("A seat number already exists for this show");
        }

        OffsetDateTime createdAt = OffsetDateTime.now();
        List<Seat> seats = new ArrayList<>();
        for (String seatNumber : request.getSeatNumbers()) {
            Seat seat = new Seat();
            seat.setId(UUID.randomUUID());
            seat.setShowId(showId);
            seat.setCategoryId(category.getId());
            seat.setSeatNumber(seatNumber);
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setCreatedAt(createdAt);
            seats.add(seat);
        }
        return seatRepository.saveAll(seats);
    }

    public List<Seat> getSeats(UUID showId, SeatStatus status) {
        showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));
        if (status == null) {
            return seatRepository.findByShowId(showId);
        }
        return seatRepository.findByShowIdAndStatus(showId, status);
    }

    private void requireShowAdmin(UUID showId, String userId) {
        if (!showAdminRepository.existsByShowIdAndUserId(showId, userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "User is not an admin of show: " + showId);
        }
    }
}