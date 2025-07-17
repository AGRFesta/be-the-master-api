-- Create the new ENUM type for supported languages
CREATE TYPE supported_language_enum AS ENUM (
    'IT',
    'EN'
);

-- Add a new column 'language' with ENUM type in the translations table
ALTER TABLE btm.translations
ADD COLUMN language supported_language_enum;

-- Migrate existing data from language_code (TEXT) to language (ENUM)
UPDATE btm.translations
SET language = UPPER(language_code)::supported_language_enum;

-- Temporarily drop NOT NULL constraint from old language_code column to allow removal
ALTER TABLE btm.translations
ALTER COLUMN language_code DROP NOT NULL;

-- Drop the old language_code column
ALTER TABLE btm.translations
DROP COLUMN language_code;

-- Enforce NOT NULL constraint on the new language column
ALTER TABLE btm.translations
ALTER COLUMN language SET NOT NULL;
