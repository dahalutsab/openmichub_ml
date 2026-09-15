-- Discovery for people who have not signed in, and a record of what anyone was shown.
--
-- V6 recorded what signed-in people did and nothing else. That left two holes.
--
-- Most visitors never sign in before they decide whether the platform has anyone worth booking,
-- and every one of them got the same front page: the catalogue ordered by rating, identical for the
-- first visit and the fiftieth. So a visitor is now identified by a random id their browser keeps -
-- not an IP address, not a fingerprint - and what they search for and open is recorded against it.
-- Signing in moves those rows onto the account (`visitor_id` cleared, `user_id` set), and rows that
-- are never claimed are deleted after 90 days by the API's retention job.
--
-- And nothing recorded what was *shown*. A click on the first result and a click on the twentieth
-- are different evidence, and an artist shown on every visit and never opened is evidence too; a log
-- of actions alone cannot tell any of that apart. `discovery_impression` is one row per ranked list
-- served, holding the artist ids in the order they were shown, and a CLICK row in
-- `user_interaction` points back at it with the position that was chosen. This is also the training
-- label the ranker has been waiting for - see ml_service/README.md, "Retraining on real data".

ALTER TABLE user_interaction ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE user_interaction ADD COLUMN IF NOT EXISTS visitor_id VARCHAR(64);
-- For a CLICK: which served list it came from, and where in that list the artist was.
ALTER TABLE user_interaction ADD COLUMN IF NOT EXISTS request_id UUID;
ALTER TABLE user_interaction ADD COLUMN IF NOT EXISTS position INTEGER;

-- Every row belongs to someone, whether an account or a browser.
ALTER TABLE user_interaction DROP CONSTRAINT IF EXISTS ck_user_interaction_actor;
ALTER TABLE user_interaction ADD CONSTRAINT ck_user_interaction_actor
    CHECK (user_id IS NOT NULL OR visitor_id IS NOT NULL);

-- One visitor's recent history, the read personalisation makes for an anonymous caller.
CREATE INDEX IF NOT EXISTS ix_user_interaction_visitor
    ON user_interaction (visitor_id, created_date DESC)
    WHERE visitor_id IS NOT NULL;

-- Platform-wide reads by recency: what is in demand this month, and who is opened alongside whom.
CREATE INDEX IF NOT EXISTS ix_user_interaction_recent_artist
    ON user_interaction (created_date DESC, artist_id)
    WHERE artist_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS discovery_impression (
    request_id   UUID         PRIMARY KEY,
    user_id      BIGINT       REFERENCES users (id) ON DELETE CASCADE,
    visitor_id   VARCHAR(64),
    -- HOME (browse with nothing stated), BROWSE (filters), SEARCH (words).
    surface      VARCHAR(32)  NOT NULL,
    search_query TEXT,
    -- In the order shown. An array rather than a row per artist: a list is written and read whole,
    -- and twenty-four rows per page view would make this the largest table in the schema for no
    -- query that needs them apart.
    artist_ids   BIGINT[]     NOT NULL,
    strategy     VARCHAR(160),
    created_date TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_discovery_impression_user
    ON discovery_impression (user_id, created_date DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_discovery_impression_visitor
    ON discovery_impression (visitor_id, created_date DESC)
    WHERE visitor_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_discovery_impression_recent
    ON discovery_impression (created_date DESC);
