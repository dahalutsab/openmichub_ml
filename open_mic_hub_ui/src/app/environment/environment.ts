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
};
