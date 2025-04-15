ALTER TABLE btm.rules_bits RENAME TO text_bits;
ALTER TABLE btm.rules_embeddings RENAME TO embeddings;
ALTER TABLE btm.embeddings RENAME CONSTRAINT fk_rules_bits TO fk_text_bit;
ALTER TABLE btm.embeddings RENAME COLUMN rule_bit_id TO text_bit_id;

CREATE TYPE topic_enum AS ENUM ('RULE', 'LORE');

ALTER TABLE btm.text_bits
ADD COLUMN topic topic_enum NOT NULL DEFAULT 'RULE';
