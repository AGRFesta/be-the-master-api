CREATE TABLE IF NOT EXISTS btm.translations (
    id UUID PRIMARY KEY,
    text_bit_id UUID NOT NULL REFERENCES btm.text_bits(id) ON DELETE CASCADE,
    language_code TEXT NOT NULL,
    original BOOLEAN NOT NULL DEFAULT FALSE,
    text TEXT NOT NULL,
    embedding_status embedding_status_enum NOT NULL DEFAULT 'UNEMBEDDED',
    created_on TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE btm.embeddings
DROP COLUMN IF EXISTS game,
DROP COLUMN IF EXISTS text_bit_id,
DROP COLUMN IF EXISTS text,
ADD COLUMN IF NOT EXISTS translation_id UUID REFERENCES btm.translations(id) ON DELETE CASCADE;

DELETE FROM btm.embeddings
WHERE translation_id IS NULL;

INSERT INTO btm.translations (
    id, text_bit_id, language_code, original, text, embedding_status, created_on
)
SELECT
    gen_random_uuid(),
    tb.id,
    'it',
    TRUE,
    tb.text,
    'UNEMBEDDED',
    tb.created_on
FROM btm.text_bits tb;

ALTER TABLE btm.text_bits DROP COLUMN IF EXISTS text;
ALTER TABLE btm.text_bits DROP COLUMN IF EXISTS embedding_status;

ALTER TABLE btm.embeddings
ALTER COLUMN translation_id SET NOT NULL;
