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

-- Table: chunks
-- Stores atomic pieces of game text with topic classification
CREATE TABLE btm.chunks (
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
    text_bit_id UUID NOT NULL REFERENCES btm.chunks(id) ON DELETE CASCADE,
    language_code TEXT NOT NULL,                          -- ISO code (e.g., 'en', 'it')
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

-- An HNSW index constructs a multilayer graph where a path between any pair of vertices can be traversed in a small
-- number of steps. HNSW has better query performance than IVFFlat (in terms of speed-recall tradeoff), but has slower
-- build times and uses more memory.
-- Also, an HNSW index can be created without any data in the table since there isn’t a training step required,
-- unlike in IVFFlat.
CREATE INDEX ON btm.embeddings USING hnsw (vector vector_cosine_ops);

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
