-- Ensure extensions for UUID
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------
-- 1. Create table btm.games
-- -----------------------------
CREATE TABLE btm.games (
    id UUID PRIMARY KEY,
    name TEXT UNIQUE NOT NULL,
    description TEXT
);

-- Populate with old enum values (generate UUIDs here)
INSERT INTO btm.games (id, name)
VALUES
    (gen_random_uuid(), 'MAUSRITTER'),
    (gen_random_uuid(), 'MORG_BORK'),
    (gen_random_uuid(), 'BLOOD_BOWL'),
    (gen_random_uuid(), 'DND'),
    (gen_random_uuid(), 'WILDSEA'),
    (gen_random_uuid(), 'SALVAGE_UNION'),
    (gen_random_uuid(), 'TRENCH_CRUSADE'),
    (gen_random_uuid(), 'FULL_SPECTRUM_DOMINANCE');

-- -----------------------------
-- 2. Migrate btm.chunks
-- -----------------------------
ALTER TABLE btm.chunks ADD COLUMN game_id UUID;

UPDATE btm.chunks c
SET game_id = g.id
FROM btm.games g
WHERE g.name = c.game::TEXT;

ALTER TABLE btm.chunks
    ALTER COLUMN game_id SET NOT NULL,
    ADD CONSTRAINT chunks_game_fk FOREIGN KEY (game_id) REFERENCES btm.games(id);

ALTER TABLE btm.chunks DROP COLUMN game;

-- -----------------------------
-- 3. Migrate btm.party
-- -----------------------------
ALTER TABLE btm.party ADD COLUMN game_id UUID;

UPDATE btm.party p
SET game_id = g.id
FROM btm.games g
WHERE g.name = p.game::TEXT;

ALTER TABLE btm.party
    ALTER COLUMN game_id SET NOT NULL,
    ADD CONSTRAINT party_game_fk FOREIGN KEY (game_id) REFERENCES btm.games(id);

ALTER TABLE btm.party DROP COLUMN game;

-- -----------------------------
-- 4. Migrate btm.glossary
-- -----------------------------
ALTER TABLE btm.glossary ADD COLUMN game_id UUID;

UPDATE btm.glossary gl
SET game_id = g.id
FROM btm.games g
WHERE g.name = gl.game::TEXT;

ALTER TABLE btm.glossary
    ALTER COLUMN game_id SET NOT NULL,
    ADD CONSTRAINT glossary_game_fk FOREIGN KEY (game_id) REFERENCES btm.games(id);

ALTER TABLE btm.glossary DROP COLUMN game;

-- -----------------------------
-- 5. Remove old game_enum
-- -----------------------------
DROP TYPE IF EXISTS game_enum;

-- -----------------------------
-- 6. Create supported_language_enum
-- -----------------------------
CREATE TYPE supported_language_enum AS ENUM (
    'IT',
    'EN'
);

-- -----------------------------
-- 7. Migrate btm.translations
-- -----------------------------
ALTER TABLE btm.translations ADD COLUMN language supported_language_enum;

UPDATE btm.translations
SET language = UPPER(language_code)::supported_language_enum;

ALTER TABLE btm.translations DROP COLUMN language_code;

ALTER TABLE btm.translations
    ALTER COLUMN language SET NOT NULL;
