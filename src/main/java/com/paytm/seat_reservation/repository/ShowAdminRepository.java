package com.paytm.seat_reservation.repository;

import com.paytm.seat_reservation.entity.ShowAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShowAdminRepository extends JpaRepository<ShowAdmin, UUID> {

    boolean existsByShowIdAndUserId(UUID showId, String userId);
}