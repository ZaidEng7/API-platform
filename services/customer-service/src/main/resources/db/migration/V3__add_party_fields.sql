ALTER TABLE customers
    ADD COLUMN phone VARCHAR(50),
    ADD COLUMN date_of_birth DATE,
    ADD COLUMN party_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL',
    ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE customers SET updated_at = created_at WHERE updated_at IS NULL;

ALTER TABLE customers ALTER COLUMN updated_at SET NOT NULL;
