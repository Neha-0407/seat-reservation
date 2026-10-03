package com.paytm.seat_reservation.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserShowLockId implements Serializable {
	private UUID showId;
	private String userId;

	public UserShowLockId() {}

	public UserShowLockId(UUID showId, String userId) {
		this.showId = showId;
		this.userId = userId;
	}

	public UUID getShowId() { return showId; }
	public void setShowId(UUID showId) { this.showId = showId; }
	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof UserShowLockId that)) return false;
		return Objects.equals(showId, that.showId) && Objects.equals(userId, that.userId);
	}

	@Override
	public int hashCode() { return Objects.hash(showId, userId); }
}
