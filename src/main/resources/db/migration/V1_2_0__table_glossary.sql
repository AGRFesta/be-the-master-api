CREATE TABLE btm.glossary (
    term TEXT PRIMARY KEY NOT NULL,            -- Original term
    tran TEXT NOT NULL,                        -- Translation of the term
    game game_enum NOT NULL,                   -- Game associated with the term
    created_on TIMESTAMP DEFAULT NOW(),        -- Timestamp of when the row was created
    updated_on TIMESTAMP DEFAULT NOW()         -- Timestamp of the last update
);
