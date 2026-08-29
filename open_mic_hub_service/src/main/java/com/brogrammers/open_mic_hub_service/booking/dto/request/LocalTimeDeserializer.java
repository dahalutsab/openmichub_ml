package com.brogrammers.open_mic_hub_service.booking.dto.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Reads a time of day from either the clock format the web client sends or a plain ISO one.
 *
 * <p>This used to accept only {@code h:mm a} ("8:00 PM"). Anything else — "20:00", "20:00:00",
 * the shapes any HTTP client or test would reach for first — threw, and the failure surfaced as a
 * generic 500, so it looked identical to a broken server rather than a rejected format.
 *
 * <p>The formats are tried in order and the first that parses wins. They cannot collide: only one
 * of them accepts a given string.
 */
public class LocalTimeDeserializer extends JsonDeserializer<LocalTime> {

    private static final List<DateTimeFormatter> FORMATS = List.of(
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),   // 8:00 PM  - the web client
            DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH),  // 08:00 PM
            DateTimeFormatter.ISO_LOCAL_TIME                          // 20:00, 20:00:00
    );

    @Override
    public LocalTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String raw = parser.getText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String time = raw.trim().toUpperCase(Locale.ENGLISH);

        for (DateTimeFormatter format : FORMATS) {
            try {
                return LocalTime.parse(time, format);
            } catch (DateTimeParseException ignored) {
                // Try the next shape.
            }
        }
        throw new IllegalArgumentException(
                "'" + raw + "' is not a time. Use \"8:00 PM\" or \"20:00\".");
    }
}
