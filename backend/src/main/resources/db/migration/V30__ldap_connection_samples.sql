CREATE TABLE ldap_connection_samples (
    id          BIGSERIAL PRIMARY KEY,
    sampled_at  TIMESTAMP NOT NULL DEFAULT now(),
    success     BOOLEAN NOT NULL,
    latency_ms  BIGINT,
    error_type  VARCHAR(64)
);

CREATE INDEX idx_ldap_connection_samples_sampled_at ON ldap_connection_samples (sampled_at DESC);
