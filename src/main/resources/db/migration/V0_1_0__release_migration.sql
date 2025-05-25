-- Create schema and required extension
CREATE SCHEMA IF NOT EXISTS btm;
CREATE EXTENSION IF NOT EXISTS vector;

-- Enum definitions
CREATE TYPE game_enum AS ENUM (
    'MAUSRITTER',
    'MORG_BORK',
    'BLOOD_BOWL',
    'DND',
    'WILDSEA',
    'SALVAGE_UNION',
    'TRENCH_CRUSADE',
    'FULL_SPECTRUM_DOMINANCE'
);

CREATE TYPE embedding_status_enum AS ENUM (
    'UNEMBEDDED',
    'IN_PROGRESS',
    'EMBEDDED'
);

CREATE TYPE topic_enum AS ENUM (
    'RULE',
    'LORE'
);

-- Table: text_bits
-- Stores atomic pieces of game text with topic classification
CREATE TABLE btm.text_bits (
    id UUID PRIMARY KEY,                      -- Unique identifier
    game game_enum NOT NULL,                  -- Game this text bit belongs to
    topic topic_enum NOT NULL DEFAULT 'RULE', -- Type of content (rule, lore, etc.)
    created_on TIMESTAMP NOT NULL,            -- Creation timestamp
    updated_on TIMESTAMP                      -- Last update timestamp (nullable)
);

-- Table: translations
-- Stores multilingual versions of each text bit
CREATE TABLE btm.translations (
    id UUID PRIMARY KEY,                                  -- Unique identifier
    text_bit_id UUID NOT NULL REFERENCES btm.text_bits(id) ON DELETE CASCADE,
    language_code TEXT NOT NULL,                          -- ISO code (e.g., 'en', 'it')
    original BOOLEAN NOT NULL DEFAULT FALSE,              -- Whether this is the original version
    text TEXT NOT NULL,                                   -- The actual translated text
    embedding_status embedding_status_enum NOT NULL DEFAULT 'UNEMBEDDED',
    created_on TIMESTAMP NOT NULL DEFAULT NOW()           -- Creation timestamp
);

-- Table: embeddings
-- Stores vector representations of translated text
CREATE TABLE btm.embeddings (
    id UUID PRIMARY KEY,                                  -- Unique identifier
    translation_id UUID NOT NULL REFERENCES btm.translations(id) ON DELETE CASCADE,
    vector VECTOR(1024) NOT NULL,                         -- 1024-dimensional embedding vector
    created_on TIMESTAMP NOT NULL                         -- Creation timestamp
);

-- Index: vector similarity search optimization
CREATE INDEX ON btm.embeddings USING ivfflat (vector) WITH (lists = 100);

-- Table: party
-- Represents a group of characters in a game session
CREATE TABLE btm.party (
    id UUID PRIMARY KEY,                  -- Unique identifier
    name TEXT NOT NULL,                   -- Party name
    game game_enum NOT NULL,              -- Game the party belongs to
    created_on TIMESTAMP NOT NULL,        -- Creation timestamp
    updated_on TIMESTAMP                  -- Last update timestamp (nullable)
);

-- Table: character
-- Stores individual character data linked to a party
CREATE TABLE btm.character (
    id UUID PRIMARY KEY,                                   -- Unique identifier
    party_id UUID NOT NULL REFERENCES btm.party(id) ON DELETE CASCADE,
    sheet JSONB NOT NULL,                                  -- Character sheet (as structured JSON)
    created_on TIMESTAMP NOT NULL,                         -- Creation timestamp
    updated_on TIMESTAMP                                   -- Last update timestamp (nullable)
);

-- Table: glossary
-- Stores terminology and translations per game
CREATE TABLE btm.glossary (
    term TEXT PRIMARY KEY NOT NULL,           -- Original term
    tran TEXT NOT NULL,                       -- Translation
    game game_enum NOT NULL,                  -- Associated game
    created_on TIMESTAMP DEFAULT NOW(),       -- Creation timestamp
    updated_on TIMESTAMP DEFAULT NOW()        -- Last update timestamp
);
