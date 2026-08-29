-- Social sign-in (Google, Facebook).
--
-- An account created through a provider has no password of its own: the provider vouches for the
-- person, and there is nothing for this application to store or check. `password` was NOT NULL,
-- which left only bad options — a placeholder hash that looks like a credential, or a random one
-- nobody can ever use. Making it nullable says what is true: this account does not have one.
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

-- Which provider vouches for the account, and the immutable id that provider uses for the person.
--
-- The id matters more than the email. Providers let people change their address, and matching on
-- email alone means a changed address silently becomes a different account — or worse, someone
-- else's. Existing rows are LOCAL: they authenticate with the password above.
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE users ADD COLUMN provider_id   VARCHAR(191);

-- One account per person per provider. Partial, because every local account has a NULL
-- provider_id and a plain unique index would allow only one of them.
CREATE UNIQUE INDEX ux_users_provider_identity
    ON users (auth_provider, provider_id)
    WHERE provider_id IS NOT NULL;

-- A local account must still have a password; only a provider-backed one may omit it. Enforced
-- here rather than in the application because it is an invariant of the row, not of one code path.
ALTER TABLE users ADD CONSTRAINT ck_users_local_has_password
    CHECK (auth_provider <> 'LOCAL' OR password IS NOT NULL);
