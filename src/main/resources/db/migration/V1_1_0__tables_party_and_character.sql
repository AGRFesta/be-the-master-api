CREATE TABLE btm.party (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    game game_enum NOT NULL,
    created_on TIMESTAMP NOT NULL,
    updated_on TIMESTAMP NULL
);

CREATE TABLE btm.character (
    id UUID PRIMARY KEY,
    party_id UUID NOT NULL REFERENCES btm.party(id) ON DELETE CASCADE,
    sheet JSONB NOT NULL,
    created_on TIMESTAMP NOT NULL,
    updated_on TIMESTAMP NULL
);
