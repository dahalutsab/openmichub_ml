package com.brogrammers.open_mic_hub_service.security;

import lombok.Getter;
import org.springframework.http.HttpMethod;

@Getter
public enum WHITE_LIST_URLS {
    AUTH("/api/v1/auth/**", new HttpMethod[]{HttpMethod.GET, HttpMethod.POST}),
    SWAGGER("/v1/swagger-ui/**", new HttpMethod[]{HttpMethod.GET}),
    SWAGGER_API_DOCS("/v1/api-docs/**", new HttpMethod[]{HttpMethod.GET}),
    GET_GENRES("/api/v1/genre", new HttpMethod[]{HttpMethod.GET}),
    ARTIST_CALENDAR("/api/v1/artist/calendar/**", new HttpMethod[]{HttpMethod.GET}),
    PUBLIC_APIS("/api/v1/public/**", new HttpMethod[]{HttpMethod.GET}),
    FILES("/media/**", new HttpMethod[]{HttpMethod.GET}),
    // Ratings drive artist discovery, so they are readable without an account.
    REVIEWS_BY_ARTIST("/api/v1/reviews/artist/**", new HttpMethod[]{HttpMethod.GET}),
    // Gateway callbacks. These verify the payment with Khalti before settling anything.
    PAYMENT_CALLBACK("/api/v1/payments/callback", new HttpMethod[]{HttpMethod.POST}),
    WITHDRAW_CALLBACK("/api/v1/artist/withdraw/callback", new HttpMethod[]{HttpMethod.POST}),
    BOT("/api/v1/chat/**", new HttpMethod[]{HttpMethod.POST});

    private final String url;
    private final HttpMethod[] methods;

    WHITE_LIST_URLS(String url, HttpMethod[] methods) {
        this.url = url;
        this.methods = methods;
    }
}