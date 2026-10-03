package com.paytm.seat_reservation.controller;

import com.paytm.seat_reservation.dto.CreateShowRequest;
import com.paytm.seat_reservation.entity.Show;
import com.paytm.seat_reservation.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/shows")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Show createShow(Authentication authentication, @Valid @RequestBody CreateShowRequest request) {
        return showService.createShow(authentication.getName(), request);
    }

    @GetMapping("/{showId}")
    public Show getShow(@PathVariable UUID showId) {
        return showService.getShow(showId);
    }
}