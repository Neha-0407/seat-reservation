package com.paytm.seat_reservation.repository;

import com.paytm.seat_reservation.entity.UserShowLock;
import com.paytm.seat_reservation.entity.UserShowLockId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserShowLockRepository extends JpaRepository<UserShowLock, UserShowLockId> {

    List<UserShowLock> findByIdShowId(UUID showId);

    Optional<UserShowLock> findByIdShowIdAndIdUserId(UUID showId, String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lockRow from UserShowLock lockRow where lockRow.id.showId = :showId and lockRow.id.userId = :userId")
    Optional<UserShowLock> findForUpdateByShowIdAndUserId(@Param("showId") UUID showId, @Param("userId") String userId);

    @Modifying
    @Query(value = "INSERT INTO user_show_locks (show_id, user_id) VALUES (:showId, :userId) ON CONFLICT (show_id, user_id) DO NOTHING", nativeQuery = true)
    int createLockIfMissing(@Param("showId") UUID showId, @Param("userId") String userId);

    boolean existsByIdShowIdAndIdUserId(UUID showId, String userId);
}
