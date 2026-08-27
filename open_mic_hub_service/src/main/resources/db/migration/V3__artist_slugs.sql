-- Public URLs for artists.
--
-- Profiles are public and meant to be found, so /artists/201 is the wrong shape of URL: it says
-- nothing to a reader, nothing to a search engine, and it invites walking the catalogue an id at a
-- time. A slug fixes all three without touching the primary key, which stays a bigint and stays
-- what every foreign key and internal API refers to.

ALTER TABLE artists ADD COLUMN slug varchar(160);

-- Backfill from the stage name.
--
-- Two artists can normalise to the same slug even though stage_name is unique ("The Velvet Club"
-- and "the velvet club!" both give "the-velvet-club"), so duplicates after the first are suffixed
-- with the id. Truncation happens before the collision check, otherwise cutting two long names to
-- the same 120 characters would produce a duplicate the check never saw.
WITH normalised AS (
    SELECT id,
           NULLIF(
               trim(BOTH '-' FROM
                   left(lower(regexp_replace(stage_name, '[^a-zA-Z0-9]+', '-', 'g')), 120)
               ),
               ''
           ) AS base
    FROM artists
),
numbered AS (
    SELECT id,
           base,
           ROW_NUMBER() OVER (PARTITION BY base ORDER BY id) AS occurrence
    FROM normalised
)
UPDATE artists a
SET slug = CASE
               -- A stage name of only punctuation leaves nothing to slugify.
               WHEN n.base IS NULL      THEN 'artist-' || a.id
               WHEN n.occurrence = 1    THEN n.base
               ELSE n.base || '-' || a.id
           END
FROM numbered n
WHERE n.id = a.id;

ALTER TABLE artists ALTER COLUMN slug SET NOT NULL;

-- Unique because it addresses a single artist, indexed because every public profile view is a
-- lookup by it.
CREATE UNIQUE INDEX ux_artists_slug ON artists (slug);
