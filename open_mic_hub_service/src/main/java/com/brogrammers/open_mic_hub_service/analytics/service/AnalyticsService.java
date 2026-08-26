package com.brogrammers.open_mic_hub_service.analytics.service;

import com.brogrammers.open_mic_hub_service.analytics.dto.AnalyticsDtos.AdminOverview;
import com.brogrammers.open_mic_hub_service.analytics.dto.AnalyticsDtos.ArtistOverview;
import com.brogrammers.open_mic_hub_service.analytics.dto.AnalyticsDtos.BookerOverview;

public interface AnalyticsService {

    /** Platform-wide board. Window is clamped to a sane range by the implementation. */
    AdminOverview adminOverview(int windowDays);

    /** The signed-in artist's own board. */
    ArtistOverview artistOverview(int windowDays);

    /** The signed-in organizer's or audience account's board. */
    BookerOverview bookerOverview(int windowDays);
}
