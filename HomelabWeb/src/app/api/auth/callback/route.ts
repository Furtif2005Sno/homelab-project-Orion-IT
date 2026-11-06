import * as client from "openid-client";
import { NextResponse, type NextRequest } from "next/server";
import { env } from "@/lib/env";
import { isAllowed, oidcConfig } from "@/lib/oidc";
import { getSession } from "@/lib/session";

/** Completes the OIDC login: exchanges the code, checks state/PKCE and the allow-list. */
export async function GET(request: NextRequest) {
  const session = await getSession();
  const pending = session.pending;
  if (!pending) return NextResponse.redirect(new URL("/login?error=expired", env.appUrl));

  try {
    const config = await oidcConfig();
    // Rebuild the callback URL on the public origin: behind a reverse proxy the request URL is internal.
    const currentUrl = new URL(`${env.appUrl}/api/auth/callback${request.nextUrl.search}`);
    const tokens = await client.authorizationCodeGrant(config, currentUrl, {
      pkceCodeVerifier: pending.verifier,
      expectedState: pending.state,
    });
    const claims = tokens.claims();
    if (!claims) throw new Error("No ID token returned");
    if (!isAllowed(claims)) {
      session.destroy();
      return NextResponse.redirect(new URL("/login?error=forbidden", env.appUrl));
    }
    const name = [claims.name, claims.preferred_username, claims.email, claims.sub].find((value) => typeof value === "string") as string;
    session.user = { sub: claims.sub, name, email: typeof claims.email === "string" ? claims.email : undefined };
    session.pending = undefined;
    await session.save();
    return NextResponse.redirect(new URL(pending.returnTo, env.appUrl));
  } catch (error) {
    console.error("OIDC callback failed:", error);
    session.pending = undefined;
    await session.save();
    return NextResponse.redirect(new URL("/login?error=failed", env.appUrl));
  }
}
