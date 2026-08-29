-- What a person did before this search.
--
-- Ranking already knows about the request in front of it — genre, budget, city, the words typed
-- into the box. It knows nothing about the person typing them, so an organizer who has booked
-- three jazz trios and read nine jazz profiles gets the same list as a first-time visitor.
--
-- Bookings are already recorded, in `booking`, and are the strongest signal there is; nothing is
-- duplicated here. What was missing is everything short of a booking: the searches, the browse
-- filters, and the profiles someone opened and did not book. Those are the majority of what a
-- visitor does, and they are the part that says what someone is looking for *now* rather than what
-- they committed to months ago.
--
-- Written only for signed-in users. An anonymous visitor is not tracked and is not identified
-- across requests; they get the unpersonalised ranking, which is what the platform served until
-- now. Rows follow the account, so deleting a user deletes their history with it.
CREATE TABLE IF NOT EXISTS user_interaction (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Null for a search or a browse: those are about a requirement, not about one artist.
    artist_id       BIGINT       REFERENCES artists (id) ON DELETE CASCADE,
    kind            VARCHAR(32)  NOT NULL,
    search_query    TEXT,
    genre           VARCHAR(160),
    city            VARCHAR(160),
    -- The occasion — Wedding, Corporate — kept apart from `kind`, which is the sort of
    -- interaction this row records.
    occasion        VARCHAR(160),
    budget_per_hour DOUBLE PRECISION,
    created_date    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- The read this table exists for: one person's recent history, newest first, bounded by a window.
CREATE INDEX IF NOT EXISTS ix_user_interaction_user
    ON user_interaction (user_id, created_date DESC);

-- The de-duplication check on a profile view, which runs before every insert of one.
CREATE INDEX IF NOT EXISTS ix_user_interaction_user_artist
    ON user_interaction (user_id, artist_id, kind, created_date DESC)
    WHERE artist_id IS NOT NULL;
