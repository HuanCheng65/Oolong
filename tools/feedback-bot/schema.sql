-- The group's messages, kept a few days so one marked late can still be found
CREATE TABLE IF NOT EXISTS messages (
    message_id INTEGER PRIMARY KEY,
    user_id INTEGER,
    user_name TEXT,
    text TEXT,
    reply_to INTEGER,
    photo TEXT,
    date INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS messages_date ON messages (date);

-- Messages the owner marked as feedback, with the conversation around them as it was then
CREATE TABLE IF NOT EXISTS feedback (
    message_id INTEGER PRIMARY KEY,
    marked_at INTEGER NOT NULL,
    -- open, planned (taken into other work), fixed, declined or duplicate
    status TEXT NOT NULL DEFAULT 'open',
    -- What was done or decided, in a line
    note TEXT,
    -- The commit that fixed it
    fixed_by TEXT,
    context TEXT NOT NULL,
    -- The model's write-up as JSON; null until it succeeds, with the last failure beside it
    summary TEXT,
    summary_error TEXT,
    -- Earlier feedback the model found this one reports again; the status is still the owner's to set
    duplicate_of INTEGER
);

-- Added after the table was created:
-- ALTER TABLE feedback ADD COLUMN fixed_by TEXT;
-- ALTER TABLE feedback ADD COLUMN duplicate_of INTEGER;
