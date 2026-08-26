package com.brogrammers.open_mic_hub_service.analytics.controller;

import com.brogrammers.open_mic_hub_service.analytics.service.AnalyticsService;
import com.brogrammers.open_mic_hub_service.common.BaseController;
import com.brogrammers.open_mic_hub_service.common.constants.GlobalApiResponse;
import com.brogrammers.open_mic_hub_service.user_management.user.role.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard analytics.
 *
 * <p>One endpoint per role rather than one per widget. Each returns everything its dashboard draws,
 * because the panels share the same underlying scans — splitting them would mean re-reading
 * bookings and payments several times to paint a single screen.
 *
 * <p>Scope is taken from the authenticated principal, never from a request parameter: an artist
 * reads their own numbers because they are that artist, not because they asked for that id.
 *
 * <p>{@code days} is the reporting window. The service clamps it, so a hostile or careless value
 * cannot turn a dashboard request into a full-history scan.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analytics")
public class AnalyticsController extends BaseController {

    private final AnalyticsService analyticsService;

    /** Default window: long enough to show a trend, short enough to still bucket by day. */
    private static final String DEFAULT_WINDOW = "30";

    @PreAuthorize(UserRole.ANY_ADMIN)
    @GetMapping("/admin/overview")
    public ResponseEntity<GlobalApiResponse> adminOverview(
            @RequestParam(defaultValue = DEFAULT_WINDOW) int days) {
        return successResponse(
                analyticsService.adminOverview(days),
                "Fetched platform analytics successfully."
        );
    }

    @PreAuthorize("hasRole('ARTIST')")
    @GetMapping("/artist/overview")
    public ResponseEntity<GlobalApiResponse> artistOverview(
            @RequestParam(defaultValue = DEFAULT_WINDOW) int days) {
        return successResponse(
                analyticsService.artistOverview(days),
                "Fetched artist analytics successfully."
        );
    }

    @PreAuthorize(UserRole.ANY_BOOKER)
    @GetMapping("/booker/overview")
    public ResponseEntity<GlobalApiResponse> bookerOverview(
            @RequestParam(defaultValue = DEFAULT_WINDOW) int days) {
        return successResponse(
                analyticsService.bookerOverview(days),
                "Fetched booking analytics successfully."
        );
    }
}
