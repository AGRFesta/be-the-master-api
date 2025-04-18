DELETE FROM btm.embeddings;
ALTER TABLE btm.embeddings DROP COLUMN vector;
ALTER TABLE btm.embeddings ADD COLUMN vector VECTOR(1024) NOT NULL;
