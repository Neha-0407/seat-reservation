package com.paytm.seat_reservation.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_show_locks")
public class UserShowLock {

    @EmbeddedId
    private UserShowLockId id;

    public UserShowLock() {
    }

    public UserShowLock(UserShowLockId id) {
        this.id = id;
    }

    public UserShowLockId getId() {
        return id;
    }

    public void setId(UserShowLockId id) {
        this.id = id;
    }
}
