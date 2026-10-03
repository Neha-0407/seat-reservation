package com.paytm.seat_reservation.repository;

import com.paytm.seat_reservation.entity.UserShowLock;
import com.paytm.seat_reservation.entity.UserShowLockId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserShowLockRepository extends JpaRepository<UserShowLock, UserShowLockId> {

    List<UserShowLock> findByIdShowId(UUID showId);

    Optional<UserShowLock> findByIdShowIdAndIdUserId(UUID showId, String userId);

    boolean existsByIdShowIdAndIdUserId(UUID showId, String userId);
}
