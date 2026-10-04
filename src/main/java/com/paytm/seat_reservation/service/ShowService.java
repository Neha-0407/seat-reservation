package com.paytm.seat_reservation.service;

import com.paytm.seat_reservation.dto.CreateShowRequest;
import com.paytm.seat_reservation.dto.ShowStateResponse;
import com.paytm.seat_reservation.entity.Seat;
import com.paytm.seat_reservation.entity.SeatCategory;
import com.paytm.seat_reservation.entity.Show;
import com.paytm.seat_reservation.entity.ShowAdmin;
import com.paytm.seat_reservation.entity.SeatStatus;
import com.paytm.seat_reservation.repository.SeatCategoryRepository;
import com.paytm.seat_reservation.repository.SeatRepository;
import com.paytm.seat_reservation.repository.ShowAdminRepository;
import com.paytm.seat_reservation.repository.ShowRepository;
import com.paytm.seat_reservation.metrics.ReservationMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final ShowAdminRepository showAdminRepository;
    private final SeatRepository seatRepository;
    private final SeatCategoryRepository seatCategoryRepository;
    private final ReservationMetrics reservationMetrics;

    public ShowService(
            ShowRepository showRepository,
            ShowAdminRepository showAdminRepository,
            SeatRepository seatRepository,
            SeatCategoryRepository seatCategoryRepository,
            ReservationMetrics reservationMetrics
    ) {
        this.showRepository = showRepository;
        this.showAdminRepository = showAdminRepository;
        this.seatRepository = seatRepository;
        this.seatCategoryRepository = seatCategoryRepository;
        this.reservationMetrics = reservationMetrics;
    }

    @Transactional
    public ShowStateResponse createShow(String creatorUserId, CreateShowRequest request) {
        if (new HashSet<>(request.getSeats()).size() != request.getSeats().size()) {
            throw new IllegalArgumentException("Seat numbers must be unique");
        }

        Show show = new Show();
        show.setId(UUID.randomUUID());
        show.setName(request.getName());
        show.setPricePaise(request.getPricePaise());
        show.setTotalSeats(request.getSeats().size());
        show.setPerUserLimit(request.getPerUserLimit());
        show.setCreatedAt(OffsetDateTime.now());
        show = showRepository.save(show);
        UUID showId = show.getId();

        SeatCategory category = new SeatCategory();
        category.setId(UUID.randomUUID());
        category.setShowId(showId);
        category.setCategoryName("General");
        category.setPricePaise(request.getPricePaise());
        SeatCategory savedCategory = seatCategoryRepository.save(category);
        UUID categoryId = savedCategory.getId();

        OffsetDateTime createdAt = OffsetDateTime.now();
        List<Seat> seats = request.getSeats().stream().map(seatNumber -> {
            Seat seat = new Seat();
            seat.setId(UUID.randomUUID());
            seat.setShowId(showId);
            seat.setCategoryId(categoryId);
            seat.setSeatNumber(seatNumber);
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setCreatedAt(createdAt);
            return seat;
        }).toList();
        seatRepository.saveAll(seats);
        reservationMetrics.registerShow(showId, seats.size());

        ShowAdmin showAdmin = new ShowAdmin();
        showAdmin.setId(UUID.randomUUID());
        showAdmin.setShowId(show.getId());
        showAdmin.setUserId(creatorUserId);
        showAdmin.setCreatedAt(OffsetDateTime.now());
        showAdminRepository.save(showAdmin);
        return getShowState(showId);
    }

    public Show getShow(UUID showId) {
        return showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));
    }

    public ShowStateResponse getShowState(UUID showId) {
        Show show = getShow(showId);
        List<Seat> seats = seatRepository.findByShowId(showId);
        Map<UUID, SeatCategory> categoriesById = new HashMap<>();
        for (SeatCategory category : seatCategoryRepository.findByShowId(showId)) {
            categoriesById.put(category.getId(), category);
        }

        long availableSeats = 0;
        long heldSeats = 0;
        long confirmedSeats = 0;
        for (Seat seat : seats) {
            if (seat.getStatus() == SeatStatus.AVAILABLE) {
                availableSeats++;
            } else if (seat.getStatus() == SeatStatus.CONFIRMED) {
                confirmedSeats++;
            }
        }

        List<ShowStateResponse.SeatState> seatStates = seats.stream()
                .sorted((first, second) -> first.getSeatNumber().compareTo(second.getSeatNumber()))
                .map(seat -> {
                    SeatCategory category = categoriesById.get(seat.getCategoryId());
                    long pricePaise = category == null ? 0L : category.getPricePaise();
                    return new ShowStateResponse.SeatState(
                            seat.getId(),
                            seat.getSeatNumber(),
                            seat.getCategoryId(),
                            pricePaise,
                            seat.getStatus().name().toLowerCase()
                    );
                })
                .toList();

        return new ShowStateResponse(
                show.getId(),
                show.getName(),
                seats.size(),
                show.getTotalSeats(),
                show.getPerUserLimit(),
                availableSeats,
                heldSeats,
                confirmedSeats,
                seatStates
        );
    }
}
