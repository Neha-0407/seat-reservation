package com.paytm.seat_reservation.controller;

import com.paytm.seat_reservation.dto.CreateSeatCategoryRequest;
import com.paytm.seat_reservation.entity.SeatCategory;
import com.paytm.seat_reservation.service.SeatCategoryService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shows/{showId}/categories")
public class SeatCategoryController {

    private final SeatCategoryService seatCategoryService;

    public SeatCategoryController(SeatCategoryService seatCategoryService) {
        this.seatCategoryService = seatCategoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SeatCategory createCategory(
            @PathVariable UUID showId,
            Authentication authentication,
            @Valid @RequestBody CreateSeatCategoryRequest request
    ) {
        return seatCategoryService.createCategory(showId, authentication.getName(), request);
    }

    @GetMapping
    public List<SeatCategory> getCategories(@PathVariable UUID showId) {
        return seatCategoryService.getCategories(showId);
    }
}