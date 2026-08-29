/**
 * Backend location, defined once.
 *
 * Six services and components used to carry their own `http://localhost:8181/...`
 * string, so pointing the app at a different backend meant editing all of them
 * and missing one. `baseUrl` keeps its previous meaning, and `wsUrl` exists
 * because the STOMP endpoint sits outside the `/api/v1` prefix.
 */
const host = 'http://localhost:8181';

export const environment = {
    production: false,
    host,
    baseUrl: `${host}/api/v1`,
    wsUrl: `${host}/ws`,

    /**
     * Whether to offer Google and Facebook sign-in.
     *
     * The backend decides whether social sign-in actually works — it registers a provider only
     * when that provider's client id and secret are set — but the browser has no way to ask. This
     * flag keeps the buttons hidden until the credentials are in place, so nobody is offered a
     * route that dead-ends. Turn it on in the same change that sets GOOGLE_CLIENT_ID and
     * FACEBOOK_CLIENT_ID.
     */
    socialSignIn: false,
};
