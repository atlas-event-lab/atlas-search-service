
ALTER TABLE hotel_room_types DROP COLUMN id;
ALTER TABLE hotel_room_types RENAME COLUMN room_type_id TO id;

ALTER TABLE hotel_projections ADD COLUMN IF NOT EXISTS amenities JSONB;
ALTER TABLE hotel_projections ADD COLUMN IF NOT EXISTS images JSONB;
ALTER TABLE hotel_room_types ADD COLUMN IF NOT EXISTS images JSONB;
