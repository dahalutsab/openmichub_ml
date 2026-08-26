--
-- Enforces in the database the guarantees that until now lived only in Java, and
-- adds the indexes the hot queries need.
--
-- Application-level checks are the right place for a helpful error message, but
-- they cannot survive a concurrent request, a second instance, or a manual fix
-- applied straight to the database. The two constraints below are the ones where
-- losing the guarantee costs money.
--

-- A payment reference from the gateway identifies exactly one payment. The
-- callback already refuses to settle a payment twice, but two callbacks arriving
-- together can both pass that check before either commits.
CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_pidx
    ON payment (pidx)
    WHERE pidx IS NOT NULL;

-- An artist is credited once per booking. Partial, because withdrawals and
-- service fees legitimately repeat against the same booking.
CREATE UNIQUE INDEX IF NOT EXISTS ux_transaction_booking_credit
    ON transaction (booking_id, transaction_purpose)
    WHERE transaction_purpose = 'BOOKING_PAYMENT' AND booking_id IS NOT NULL;

-- One review per person per booking.
CREATE UNIQUE INDEX IF NOT EXISTS ux_review_booking_reviewer
    ON review (booking_id, reviewer_id);


-- Indexes for the queries that run on every page load.

-- Double-booking check: artist and date, narrowed to live bookings.
CREATE INDEX IF NOT EXISTS ix_booking_artist_date
    ON booking (artist_id, event_date, status);

-- An organizer's own bookings, newest first.
CREATE INDEX IF NOT EXISTS ix_booking_user
    ON booking (user_id, event_date DESC);

-- Public review listing for an artist.
CREATE INDEX IF NOT EXISTS ix_review_artist
    ON review (artist_id, created_date DESC);

-- Conversation lookups run in both directions.
CREATE INDEX IF NOT EXISTS ix_chats_sender_recipient
    ON chats (sender_id, recipient_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS ix_chats_recipient_sender
    ON chats (recipient_id, sender_id, timestamp DESC);

-- Blackout-date check during booking.
CREATE INDEX IF NOT EXISTS ix_unavailability_artist_dates
    ON artist_unavailability (artist_id, start_date, end_date);

-- Payment history for a user, and the artist ledger.
CREATE INDEX IF NOT EXISTS ix_payment_user
    ON payment (user_info_entity_id);
CREATE INDEX IF NOT EXISTS ix_transaction_wallet
    ON transaction (virtual_coin_virtual_coin_id, transaction_type, transaction_purpose);

-- OTP lookups happen by value on every verification and reset attempt.
CREATE INDEX IF NOT EXISTS ix_otp_value_purpose
    ON otp (otp_value, purpose);
