-- V1 attempt_no'yu SMALLINT yapmisti; entity Integer (INT4) bekliyor (ddl-auto: validate).
-- ER "int attemptNo" der; kolonu INTEGER'a genislet.
ALTER TABLE payment_attempts ALTER COLUMN attempt_no TYPE INTEGER;
