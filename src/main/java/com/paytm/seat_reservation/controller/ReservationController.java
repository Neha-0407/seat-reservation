package com.paytm.seat_reservation.controller;

import com.paytm.seat_reservation.dto.ReservationResponse;
import com.paytm.seat_reservation.dto.ReserveRequest;
import com.paytm.seat_reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserveSeats(
            Authentication authentication,
            @Valid @RequestBody ReserveRequest request
    ) {
        return reservationService.reserveSeats(authentication.getName(), request);
    }
}