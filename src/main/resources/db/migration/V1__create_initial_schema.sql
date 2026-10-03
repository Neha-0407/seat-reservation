
-- =========================================
-- 1. SHOWS TABLE
-- =========================================

CREATE TABLE shows(
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price_paise BIGINT NOT NULL CHECK (price_paise >= 0),
    per_user_limit INTEGER NOT NULL DEFAULT 4
        CHECK(per_user_limit BETWEEN 1 AND 4),
    total_seats INTEGER NOT NULL CHECK (total_seats >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================
-- 2. SEAT CATEGORIES TABLE
-- =========================================

CREATE TABLE seat_categories(
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id),
    category_name VARCHAR(50) NOT NULL,
    price_paise BIGINT NOT NULL CHECK (price_paise >= 0),
    -- Category name must be unique within a show
    UNIQUE (show_id, category_name),
    -- Supports composite foreign key from seats
    UNIQUE (id, show_id)
);

-- =========================================
-- 3. RESERVATIONS TABLE
-- =========================================
CREATE TABLE reservations(
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id) ,
    user_id VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(255) NOT NULL,
    amount_paise BIGINT NOT NULL CHECK (amount_paise >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMPTZ,
    UNIQUE (show_id, user_id, idempotency_key),
    UNIQUE (id, show_id)
);


-- =========================================
-- 4. SEATS TABLE
-- =========================================
CREATE TABLE seats (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id),
    category_id UUID NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'CONFIRMED')),
    reserved_by UUID REFERENCES reservations(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Seat number must be unique within a show
    UNIQUE (show_id, seat_number),

    -- Supports composite foreign key from reservation_seats
    UNIQUE (id, show_id),

    -- Category must belong to the same show
    FOREIGN KEY (category_id, show_id)
        REFERENCES seat_categories(id, show_id),

    -- Seat status and reservation owner must be consistent
    CHECK (
        (status = 'AVAILABLE' AND reserved_by IS NULL)
        OR
        (status = 'CONFIRMED' AND reserved_by IS NOT NULL)
    )
);

-- =========================================
-- 5. RESERVATION-TO-SEAT MAPPING
-- =========================================
CREATE TABLE reservation_seats (
    reservation_id UUID NOT NULL,
    seat_id UUID NOT NULL,
    show_id UUID NOT NULL REFERENCES shows(id),
    unit_price_paise BIGINT NOT NULL CHECK (unit_price_paise >= 0),

    PRIMARY KEY (reservation_id, seat_id),

    -- Reservation must belong to this show
    FOREIGN KEY (reservation_id, show_id)
        REFERENCES reservations(id, show_id),

    -- Seat must belong to this show
    FOREIGN KEY (seat_id, show_id)
        REFERENCES seats(id, show_id)
);

-- =========================================
-- 6. PER-USER, PER-SHOW BOOKING LOCKS
-- =========================================
CREATE TABLE user_show_locks (
    show_id UUID NOT NULL REFERENCES shows(id),
    user_id VARCHAR(255) NOT NULL,

    PRIMARY KEY (show_id, user_id)
);


-- =========================================
-- 7. INDEXES
-- =========================================

-- Find available or confirmed seats for a show
CREATE INDEX idx_seats_show_status
    ON seats(show_id, status);

-- Find reservations for a user and show by status
CREATE INDEX idx_reservations_user_show_status
    ON reservations(user_id, show_id, status);

-- Find reservation mappings by seat
CREATE INDEX idx_reservation_seats_seat
    ON reservation_seats(seat_id);

-- Find categories belonging to a show
CREATE INDEX idx_seat_categories_show
    ON seat_categories(show_id);