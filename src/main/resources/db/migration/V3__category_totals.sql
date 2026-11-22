CREATE TABLE category_totals (
                                 account_id    UUID   NOT NULL,
                                 month         DATE   NOT NULL,   -- first day of the month, UTC
                                 category      TEXT   NOT NULL,
                                 income_minor  BIGINT NOT NULL DEFAULT 0,
                                 expense_minor BIGINT NOT NULL DEFAULT 0,
                                 PRIMARY KEY (account_id, month, category)
);

INSERT INTO projection_checkpoints (projection_name, last_global_seq)
VALUES ('category_totals', 0);