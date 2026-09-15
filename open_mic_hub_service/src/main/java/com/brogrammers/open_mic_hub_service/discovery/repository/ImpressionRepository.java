package com.brogrammers.open_mic_hub_service.discovery.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The served-lists log, {@code discovery_impression}.
 *
 * <p>Plain JDBC rather than an entity. The row is an id and a {@code BIGINT[]} that is written once
 * and never loaded back into Java - the ML service reads it - and mapping a Postgres array through
 * Hibernate buys a custom type and a schema-validation dependency for nothing this side uses.
 */
@Repository
@RequiredArgsConstructor
public class ImpressionRepository {

    private final JdbcTemplate jdbc;

    public void insert(UUID requestId, Long userId, String visitorId, String surface,
                       String query, List<Long> artistIds, String strategy) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO discovery_impression
                        (request_id, user_id, visitor_id, surface, search_query, artist_ids,
                         strategy, created_date)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (request_id) DO NOTHING
                    """);
            Array ids = connection.createArrayOf("bigint", artistIds.toArray());
            statement.setObject(1, requestId);
            statement.setObject(2, userId);
            statement.setString(3, visitorId);
            statement.setString(4, surface);
            statement.setString(5, query);
            statement.setArray(6, ids);
            statement.setString(7, strategy);
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            return statement;
        });
    }

    /** Whether this list was served, and to whom - a click is only credited against its own list. */
    public boolean servedTo(UUID requestId, Long userId, String visitorId, long artistId) {
        // Two statements rather than one with "user_id = ? OR visitor_id = ?": a null bound to an
        // untyped parameter is something the driver cannot always infer a type for.
        String owner = userId != null ? "user_id = ?" : "visitor_id = ?";
        Object ownerValue = userId != null ? userId : visitorId;
        Integer found = jdbc.queryForObject(
                "SELECT COUNT(*) FROM discovery_impression WHERE request_id = ? "
                        + "AND ?::bigint = ANY(artist_ids) AND " + owner,
                Integer.class, requestId, artistId, ownerValue);
        return found != null && found > 0;
    }

    public int claimVisitorLists(String visitorId, Long userId) {
        return jdbc.update("""
                UPDATE discovery_impression SET user_id = ?, visitor_id = NULL
                WHERE visitor_id = ? AND user_id IS NULL
                """, userId, visitorId);
    }

    public int deleteUnclaimedBefore(LocalDateTime before) {
        return jdbc.update(
                "DELETE FROM discovery_impression WHERE user_id IS NULL AND created_date < ?",
                Timestamp.valueOf(before));
    }

    public int deleteBefore(LocalDateTime before) {
        return jdbc.update("DELETE FROM discovery_impression WHERE created_date < ?",
                Timestamp.valueOf(before));
    }
}
