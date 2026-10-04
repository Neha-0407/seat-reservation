package com.paytm.seat_reservation.controller;

import com.paytm.seat_reservation.dto.CreateSeatsRequest;
import com.paytm.seat_reservation.entity.Seat;
import com.paytm.seat_reservation.entity.SeatStatus;
import com.paytm.seat_reservation.service.SeatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/shows/{showId}/seats")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<Seat> createSeats(
            @PathVariable UUID showId,
            Authentication authentication,
            @Valid @RequestBody CreateSeatsRequest request
    ) {
        return seatService.createSeats(showId, authentication.getName(), request);
    }

    @GetMapping
    public List<Seat> getSeats(
            @PathVariable UUID showId,
            @RequestParam(required = false) SeatStatus status
    ) {
        return seatService.getSeats(showId, status);
    }
}