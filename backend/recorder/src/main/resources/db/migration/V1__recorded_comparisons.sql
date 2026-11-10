CREATE TABLE recorded_comparisons (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    payload jsonb NOT NULL
);

CREATE TABLE comparison_outbox (
    id uuid PRIMARY KEY REFERENCES recorded_comparisons (id),
    created_at timestamptz NOT NULL,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    claim_token uuid,
    published_at timestamptz
);

CREATE INDEX comparison_outbox_pending_idx
    ON comparison_outbox (next_attempt_at, created_at, id)
    WHERE published_at IS NULL;
