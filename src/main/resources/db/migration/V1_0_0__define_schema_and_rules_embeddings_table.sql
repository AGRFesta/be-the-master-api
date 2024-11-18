CREATE SCHEMA IF NOT EXISTS btm;

-- Ensure that the pgvector extension is installed
CREATE EXTENSION IF NOT EXISTS vector;

-- Create the enum for the "game" column
CREATE TYPE game_enum AS ENUM ('MAUSRITTER');

-- Create the btm.rules_embeddings table
CREATE TABLE btm.rules_embeddings (
    id UUID PRIMARY KEY,                     -- Unique identifier
    game game_enum NOT NULL,                 -- Enum for the game type
    vector VECTOR(1536) NOT NULL,            -- 1536-dimensional embedding vector
    text TEXT NOT NULL                       -- Original text used to generate the embedding
);

-- Create an index to speed up similarity searches
-- ivfflat: An indexing method optimized for approximate nearest neighbor searches on high-dimensional vectors.
-- WITH (lists = 100): Specifies the number of partitions (or "lists") used by the index.
--                     Higher values generally improve search recall but require more memory.
CREATE INDEX ON btm.rules_embeddings USING ivfflat (vector) WITH (lists = 100);
