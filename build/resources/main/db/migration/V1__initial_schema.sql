-- consumed_events: guards idempotency for all Kafka consumers (EVT-005, EVT-008)
CREATE TABLE consumed_events (
    id          UUID        NOT NULL,
    event_type  VARCHAR(100) NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_consumed_events PRIMARY KEY (id)
);
