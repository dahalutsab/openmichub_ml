package com.brogrammers.open_mic_hub_service.discovery.job;

import com.brogrammers.open_mic_hub_service.discovery.repository.ImpressionRepository;
import com.brogrammers.open_mic_hub_service.discovery.repository.UserInteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Deletes discovery history that has outlived its use.
 *
 * <p>A visitor who never signs in is identified only by a random id in their browser, and what was
 * recorded against it goes after {@value #UNCLAIMED_DAYS} days - by then its weight in any ranking
 * has decayed to almost nothing, and keeping it would only be keeping it. Served lists go after
 * {@value #IMPRESSION_DAYS} days for everyone: they are training data for the ranker, and half a
 * year is more history than anything here reads.
 *
 * <p>An account's own searches and views are left alone. They follow the account, and deleting the
 * account deletes them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscoveryRetentionJob {

    static final int UNCLAIMED_DAYS = 90;
    static final int IMPRESSION_DAYS = 180;

    private final UserInteractionRepository interactions;
    private final ImpressionRepository impressions;

    /** Nightly, at a quiet hour. */
    @Scheduled(cron = "${discovery.retention.cron:0 17 3 * * *}")
    @Transactional
    public void purge() {
        LocalDateTime now = LocalDateTime.now();
        try {
            int visitorRows = interactions.deleteUnclaimedBefore(now.minusDays(UNCLAIMED_DAYS));
            int visitorLists = impressions.deleteUnclaimedBefore(now.minusDays(UNCLAIMED_DAYS));
            int oldLists = impressions.deleteBefore(now.minusDays(IMPRESSION_DAYS));
            log.info("Discovery retention: removed {} unclaimed interactions, {} unclaimed lists, "
                    + "{} lists past {} days", visitorRows, visitorLists, oldLists, IMPRESSION_DAYS);
        } catch (Exception e) {
            log.warn("Discovery retention did not run: {}", e.getMessage());
        }
    }
}
