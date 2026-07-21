-- Optional profile picture. Stored as an object path (like ghana_card_url);
-- the profile response mints a short-lived signed URL for reads.
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(512);
