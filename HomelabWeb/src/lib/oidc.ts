import "server-only";
import * as client from "openid-client";
import { env } from "./env";

let configuration: Promise<client.Configuration> | null = null;

/** OIDC client from the issuer's discovery document (cached; retried after a failure). */
export function oidcConfig(): Promise<client.Configuration> {
  if (!env.oidcIssuer || !env.oidcClientId) {
    throw new Error("OIDC_ISSUER and OIDC_CLIENT_ID must be configured.");
  }
  if (!configuration) {
    const issuer = new URL(env.oidcIssuer);
    // A plain-HTTP issuer is only accepted when explicitly configured that way (LAN-only IdP).
    const options = issuer.protocol === "http:" ? { execute: [client.allowInsecureRequests] } : undefined;
    configuration = client
      .discovery(issuer, env.oidcClientId, env.oidcClientSecret || undefined, undefined, options)
      .catch((error) => {
        configuration = null;
        throw error;
      });
  }
  return configuration;
}

export const redirectUri = () => `${env.appUrl}/api/auth/callback`;

/** Users allowed in: the allow-list matches email, preferred username or subject. */
export function isAllowed(claims: { sub: string; email?: unknown; preferred_username?: unknown }): boolean {
  if (env.oidcAllowedUsers.length === 0) return true;
  const candidates = [claims.sub, claims.email, claims.preferred_username]
    .filter((value): value is string => typeof value === "string")
    .map((value) => value.toLowerCase());
  return candidates.some((value) => env.oidcAllowedUsers.includes(value));
}
