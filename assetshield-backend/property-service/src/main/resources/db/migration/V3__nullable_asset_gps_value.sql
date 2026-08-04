-- Assets can be documented without GPS (permission denied / indoors) or without
-- an estimated value (owner may not know it). The app already treats both as
-- optional; the NOT NULL constraints here rejected those uploads with a
-- confusing VALIDATION_FAILED. Make them nullable to match.
ALTER TABLE assets ALTER COLUMN gps_lat DROP NOT NULL;
ALTER TABLE assets ALTER COLUMN gps_lng DROP NOT NULL;
ALTER TABLE assets ALTER COLUMN estimated_value DROP NOT NULL;
