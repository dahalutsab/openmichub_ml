package com.brogrammers.open_mic_hub_service.discovery.dto;

import java.util.UUID;

/**
 * An artist chosen from a served list.
 *
 * @param requestId the list's id, as returned with it
 * @param artistId  the artist chosen
 * @param position  where that artist was in the list as served, from 0 - not where a client-side
 *                  sort moved it
 */
public record ClickRequest(UUID requestId, Long artistId, Integer position) {
}
