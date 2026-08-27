package com.brogrammers.open_mic_hub_service.reviews.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * An artist's ratings in aggregate, over every review rather than a page of them.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RatingSummaryResponse {

    /** Mean of every review, to one decimal. Zero when there are none. */
    private double average;

    /** How many reviews there are in total. */
    private long count;

    /** Counts for one through five stars, in that order. */
    private List<Long> distribution;
}
