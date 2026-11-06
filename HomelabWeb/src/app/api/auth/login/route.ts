import * as client from "openid-client";
import { NextResponse, type NextRequest } from "next/server";
import { env } from "@/lib/env";
import { oidcConfig, redirectUri } from "@/lib/oidc";
import { getSession } from "@/lib/session";

/** Starts the OIDC authorization code flow with PKCE. */
export async function GET(request: NextRequest) {
  if (env.devNoAuth) return NextResponse.redirect(new URL("/", env.appUrl));

  const returnTo = request.nextUrl.searchParams.get("returnTo") ?? "/";
  let config: client.Configuration;
  try {
    config = await oidcConfig();
  } catch (error) {
    console.error("OIDC discovery failed:", error);
    return NextResponse.redirect(new URL("/login?error=unavailable", env.appUrl));
  }
  const verifier = client.randomPKCECodeVerifier();
  const state = client.randomState();

  const session = await getSession();
  // Only same-site paths are accepted as a return target.
  session.pending = { verifier, state, returnTo: returnTo.startsWith("/") && !returnTo.startsWith("//") ? returnTo : "/" };
  await session.save();

  const url = client.buildAuthorizationUrl(config, {
    redirect_uri: redirectUri(),
    scope: env.oidcScopes,
    code_challenge: await client.calculatePKCECodeChallenge(verifier),
    code_challenge_method: "S256",
    state,
  });
  return NextResponse.redirect(url);
}
