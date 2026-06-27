
BEGIN;

UPDATE hotel_projections
SET amenities = '[]'::jsonb
WHERE amenities IS NULL;

UPDATE hotel_projections
SET images = '[]'::jsonb
WHERE images IS NULL;

UPDATE hotel_room_types
SET images = '[]'::jsonb
WHERE images IS NULL;

-- Set default
ALTER TABLE hotel_projections
    ALTER COLUMN amenities SET DEFAULT '[]'::jsonb;

ALTER TABLE hotel_projections
    ALTER COLUMN images SET DEFAULT '[]'::jsonb;

ALTER TABLE hotel_room_types
    ALTER COLUMN images SET DEFAULT '[]'::jsonb;

-- Enforce NOT NULL
ALTER TABLE hotel_projections
    ALTER COLUMN amenities SET NOT NULL;

ALTER TABLE hotel_projections
    ALTER COLUMN images SET NOT NULL;

ALTER TABLE hotel_room_types
    ALTER COLUMN images SET NOT NULL;

COMMIT;