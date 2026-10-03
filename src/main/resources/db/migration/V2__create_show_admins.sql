CREATE TABLE show_admins (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id),
    user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_show_admins_show_user UNIQUE (show_id, user_id)
);

CREATE INDEX idx_show_admins_user_show
    ON show_admins(user_id, show_id);