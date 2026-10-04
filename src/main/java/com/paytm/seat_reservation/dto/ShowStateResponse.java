package com.paytm.seat_reservation.dto;

import java.util.List;
import java.util.UUID;

public class ShowStateResponse {

    private UUID id;
    private String name;
    private int totalSeats;
    private int configuredSeatCapacity;
    private int perUserLimit;
    private long availableSeats;
    private long heldSeats;
    private long confirmedSeats;
    private List<SeatState> seats;

    public ShowStateResponse() {
    }

    public ShowStateResponse(
            UUID id,
            String name,
            int totalSeats,
            int configuredSeatCapacity,
            int perUserLimit,
            long availableSeats,
            long heldSeats,
            long confirmedSeats,
            List<SeatState> seats
    ) {
        this.id = id;
        this.name = name;
        this.totalSeats = totalSeats;
        this.configuredSeatCapacity = configuredSeatCapacity;
        this.perUserLimit = perUserLimit;
        this.availableSeats = availableSeats;
        this.heldSeats = heldSeats;
        this.confirmedSeats = confirmedSeats;
        this.seats = seats;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public int getConfiguredSeatCapacity() {
        return configuredSeatCapacity;
    }

    public int getPerUserLimit() {
        return perUserLimit;
    }

    public long getAvailableSeats() {
        return availableSeats;
    }

    public long getHeldSeats() {
        return heldSeats;
    }

    public long getConfirmedSeats() {
        return confirmedSeats;
    }

    public List<SeatState> getSeats() {
        return seats;
    }

    public record SeatState(UUID seatId, String seatNumber, UUID categoryId, long pricePaise, String status) {
    }
}