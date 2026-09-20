import Keycloak from 'keycloak-js';

/**
 * Single Keycloak instance for the app. Tokens live in memory only:
 * nothing is written to localStorage, so a stolen browser profile yields no session.
 */
const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'credisynch',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'credisynch-web',
});

export async function initKeycloak() {
  const authenticated = await keycloak.init({
    onLoad: 'login-required',
    pkceMethod: 'S256',
    checkLoginIframe: false,
  });
  // Refresh the access token before it expires so long analyst sessions do not drop.
  setInterval(() => keycloak.updateToken(60).catch(() => keycloak.login()), 30000);
  return authenticated;
}

export function currentRoles() {
  return keycloak.tokenParsed?.realm_access?.roles ?? [];
}

export default keycloak;
