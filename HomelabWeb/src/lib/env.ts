import "server-only";
import path from "node:path";

/** Server configuration, read from the environment (see .env.example). */
export const env = {
  appUrl: (process.env.APP_URL ?? "http://localhost:3000").replace(/\/+$/, ""),
  dataDir: process.env.DATA_DIR ?? path.join(process.cwd(), "data"),

  oidcIssuer: process.env.OIDC_ISSUER ?? "",
  oidcClientId: process.env.OIDC_CLIENT_ID ?? "",
  oidcClientSecret: process.env.OIDC_CLIENT_SECRET ?? "",
  oidcScopes: process.env.OIDC_SCOPES ?? "openid profile email",
  /** Optional allow-list of emails, usernames or subjects; empty means any user the IdP lets in. */
  oidcAllowedUsers: (process.env.OIDC_ALLOWED_USERS ?? "")
    .split(",")
    .map((value) => value.trim().toLowerCase())
    .filter(Boolean),

  sessionSecret: process.env.SESSION_SECRET ?? "",
  encryptionKey: process.env.HOMELAB_ENCRYPTION_KEY ?? "",

  /** Local development only: skips OIDC. Ignored in production builds. */
  devNoAuth: process.env.NODE_ENV !== "production" && process.env.HOMELAB_DEV_NO_AUTH === "1",
};

export function requireSecret(name: "SESSION_SECRET" | "HOMELAB_ENCRYPTION_KEY", value: string): string {
  if (value.length < 32) {
    throw new Error(`${name} must be set to a random string of at least 32 characters.`);
  }
  return value;
}
