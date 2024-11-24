CREATE SCHEMA IF NOT EXISTS btm;

-- Ensure that the pgvector extension is installed
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TYPE game_enum AS ENUM ('MAUSRITTER', 'MORG_BORK');
CREATE TYPE embedding_status_enum AS ENUM ('UNEMBEDDED', 'IN_PROGRESS', 'EMBEDDED');

-- Create the btm.rules_bits table
CREATE TABLE btm.rules_bits (
    id UUID PRIMARY KEY,                     -- Unique identifier
    game game_enum NOT NULL,                 -- Enum for the game type
    text TEXT NOT NULL,                      -- Rule bit text
    embedding_status embedding_status_enum NOT NULL, -- Status of embedding process
    created_on TIMESTAMP NOT NULL,           -- Creation timestamp
    updated_on TIMESTAMP                     -- Last update timestamp (nullable)
);

-- Create the btm.rules_embeddings table
CREATE TABLE btm.rules_embeddings (
    id UUID PRIMARY KEY,                     -- Unique identifier
    rule_bit_id UUID NOT NULL,               -- Foreign key reference to rules_bits
    game game_enum NOT NULL,                 -- Enum for the game type
    vector VECTOR(1536) NOT NULL,            -- 1536-dimensional embedding vector
    text TEXT NOT NULL,                      -- Original text used to generate the embedding
    created_on TIMESTAMP NOT NULL,           -- Creation timestamp

    CONSTRAINT fk_rules_bits FOREIGN KEY (rule_bit_id) REFERENCES btm.rules_bits (id) ON DELETE CASCADE
);

-- Create an index to speed up similarity searches
-- ivfflat: An indexing method optimized for approximate nearest neighbor searches on high-dimensional vectors.
-- WITH (lists = 100): Specifies the number of partitions (or "lists") used by the index.
--                     Higher values generally improve search recall but require more memory.
CREATE INDEX ON btm.rules_embeddings USING ivfflat (vector) WITH (lists = 100);
