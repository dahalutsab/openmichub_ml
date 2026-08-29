package com.brogrammers.open_mic_hub_service.booking;

import com.brogrammers.open_mic_hub_service.booking.dto.request.BookingRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reading a time of day off a booking request.
 *
 * <p>This accepted only {@code h:mm a}. An ISO time — the shape any HTTP client or test reaches
 * for first — threw, and the failure surfaced as a generic 500, indistinguishable from the server
 * being broken. Both shapes are read now, and something that is not a time is refused clearly.
 */
class LocalTimeDeserializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    private LocalTime parseStartTime(String raw) throws Exception {
        String json = """
                {"artistId":1,"venue":"v","eventType":"Corporate",
                 "eventDate":"2026-09-03","startTime":"%s","endTime":"11:00 PM"}
                """.formatted(raw);
        return objectMapper.readValue(json, BookingRequest.class).getStartTime();
    }

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @DisplayName("both the clock format the client sends and a plain ISO one")
    @CsvSource({
            "8:00 PM,   20:00",
            "08:00 PM,  20:00",
            "8:00 am,   08:00",
            "12:00 AM,  00:00",
            "12:00 PM,  12:00",
            "20:00,     20:00",
            "20:00:00,  20:00",
            "09:30,     09:30",
    })
    void readsBothFormats(String raw, String expected) throws Exception {
        assertThat(parseStartTime(raw.trim())).isEqualTo(LocalTime.parse(expected.trim()));
    }

    @Test
    @DisplayName("surrounding whitespace does not matter")
    void toleratesWhitespace() throws Exception {
        assertThat(parseStartTime("  8:00 PM  ")).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    @DisplayName("something that is not a time says so, rather than throwing something opaque")
    void rejectsNonTimes() {
        assertThatThrownBy(() -> parseStartTime("half past eight"))
                .hasMessageContaining("is not a time");
    }
}
