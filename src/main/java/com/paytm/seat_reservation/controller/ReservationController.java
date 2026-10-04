package com.paytm.seat_reservation.controller;

import com.paytm.seat_reservation.dto.ReservationResponse;
import com.paytm.seat_reservation.dto.ReserveRequest;
import com.paytm.seat_reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/shows/{showId}/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserveSeats(
            @PathVariable java.util.UUID showId,
            Authentication authentication,
            @Valid @RequestBody ReserveRequest request
    ) {
        return reservationService.reserveSeats(showId, authentication.getName(), request);
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ReservationResponse cancelReservation(
            Authentication authentication,
            @PathVariable java.util.UUID reservationId
    ) {
        return reservationService.cancelReservation(authentication.getName(), reservationId);
    }
}