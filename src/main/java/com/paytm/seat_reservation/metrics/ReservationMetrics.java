package com.paytm.seat_reservation.metrics;

import com.paytm.seat_reservation.exception.ReservationDeclineReason;
import com.paytm.seat_reservation.repository.SeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ReservationMetrics {

    private final MeterRegistry meterRegistry;
    private final SeatRepository seatRepository;
    private final Counter confirmedReservations;
    private final Counter idempotentReplays;
    private final ConcurrentMap<UUID, AtomicLong> availableSeats = new ConcurrentHashMap<>();

    public ReservationMetrics(MeterRegistry meterRegistry, SeatRepository seatRepository) {
        this.meterRegistry = meterRegistry;
        this.seatRepository = seatRepository;
        this.confirmedReservations = meterRegistry.counter("seat_reservations.confirmed");
        this.idempotentReplays = meterRegistry.counter("seat_reservations.idempotent.replays");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileAvailableSeatGauges() {
        for (Object[] result : seatRepository.countAvailableSeatsByShow()) {
            UUID showId = (UUID) result[0];
            Number count = (Number) result[1];
            setAvailableSeats(showId, count.longValue());
        }
    }

    public void registerShow(UUID showId, long availableCount) {
        afterCommit(() -> setAvailableSeats(showId, availableCount));
    }

    public void recordConfirmationAfterCommit(UUID showId, int seatCount) {
        afterCommit(() -> {
            confirmedReservations.increment();
            availableGauge(showId).addAndGet(-seatCount);
        });
    }

    public void recordCancellationAfterCommit(UUID showId, int seatCount) {
        afterCommit(() -> availableGauge(showId).addAndGet(seatCount));
    }

    public void recordIdempotentReplay() {
        idempotentReplays.increment();
    }

    public void recordDecline(ReservationDeclineReason reason) {
        meterRegistry.counter("seat_reservations.declined", "reason", reason.code()).increment();
    }

    private void setAvailableSeats(UUID showId, long count) {
        availableGauge(showId).set(count);
    }

    private AtomicLong availableGauge(UUID showId) {
        return availableSeats.computeIfAbsent(showId, id -> {
            AtomicLong count = new AtomicLong();
            Gauge.builder("seat_reservation.seats.available", count, AtomicLong::get)
                    .description("Currently available seats for a show")
                    .tag("show_id", id.toString())
                    .register(meterRegistry);
            return count;
        });
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}