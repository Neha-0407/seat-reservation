package com.paytm.seat_reservation.repository;

import com.paytm.seat_reservation.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShowRepository extends JpaRepository<Show, UUID> {
}
