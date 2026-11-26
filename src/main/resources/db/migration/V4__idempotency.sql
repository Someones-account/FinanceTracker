CREATE TABLE idempotency_keys (
                                  idempotency_key TEXT        PRIMARY KEY,
                                  account_id      UUID        NOT NULL,
                                  request_hash    TEXT        NOT NULL,
                                  status          TEXT        NOT NULL,
                                  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);