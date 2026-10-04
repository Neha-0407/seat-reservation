package com.paytm.seat_reservation.controller;

import com.paytm.seat_reservation.dto.ReservationResponse;
import com.paytm.seat_reservation.dto.ReserveRequest;
import com.paytm.seat_reservation.service.ReservationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

    private static final Logger logger = LoggerFactory.getLogger(ReservationController.class);

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
        ReservationResponse reservation = reservationService.reserveSeats(showId, authentication.getName(), request);
        logger.atInfo()
            .addKeyValue("event", "reservation_confirmed")
            .addKeyValue("request_id", MDC.get("request_id"))
            .addKeyValue("reservation_id", reservation.getReservationId())
            .addKeyValue("show_id", reservation.getShowId())
            .addKeyValue("seat_count", reservation.getSeats().size())
            .addKeyValue("amount_paise", reservation.getAmountPaise())
            .log("reservation_confirmed");
        return reservation;
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ReservationResponse cancelReservation(
            Authentication authentication,
            @PathVariable java.util.UUID reservationId
    ) {
        ReservationResponse reservation = reservationService.cancelReservation(authentication.getName(), reservationId);
        logger.atInfo()
            .addKeyValue("event", "reservation_cancelled")
            .addKeyValue("request_id", MDC.get("request_id"))
            .addKeyValue("reservation_id", reservation.getReservationId())
            .addKeyValue("show_id", reservation.getShowId())
            .addKeyValue("seat_count", reservation.getSeats().size())
            .log("reservation_cancelled");
        return reservation;
    }
}