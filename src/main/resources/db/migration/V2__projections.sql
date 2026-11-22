CREATE TABLE account_balances (
                                  account_id    UUID        PRIMARY KEY,
                                  name          TEXT        NOT NULL,
                                  currency      TEXT        NOT NULL,
                                  balance_minor BIGINT      NOT NULL,
                                  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE projection_checkpoints (
                                        projection_name TEXT   PRIMARY KEY,
                                        last_global_seq BIGINT NOT NULL
);

INSERT INTO projection_checkpoints (projection_name, last_global_seq)
VALUES ('account_balances', 0);