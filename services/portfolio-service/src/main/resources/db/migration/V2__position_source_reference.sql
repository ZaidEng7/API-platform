-- Idempotency for recordPosition: the caller (Investment Service's
-- subscription saga) may retry after a timed-out-but-actually-succeeded
-- call, or after a local transaction rollback that happened after this
-- call already succeeded. Without a reference back to what caused this
-- position, a retry silently creates a duplicate position. Nullable +
-- partial unique index (not a NOT NULL column) so any future caller that
-- doesn't have a natural reference isn't forced to invent one.
ALTER TABLE positions ADD COLUMN source_reference VARCHAR(255);
CREATE UNIQUE INDEX uq_positions_source_reference ON positions (source_reference) WHERE source_reference IS NOT NULL;
