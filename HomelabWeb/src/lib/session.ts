import "server-only";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { getIronSession, type SessionOptions } from "iron-session";
import { env, requireSecret } from "./env";

export interface SessionUser {
  sub: string;
  name: string;
  email?: string;
}

export interface SessionData {
  user?: SessionUser;
  /** OIDC login in progress (PKCE verifier + state), kept until the callback. */
  pending?: { verifier: string; state: string; returnTo: string };
}

export const SESSION_COOKIE = "homelab_session";

function options(): SessionOptions {
  return {
    cookieName: SESSION_COOKIE,
    password: requireSecret("SESSION_SECRET", env.sessionSecret),
    ttl: 60 * 60 * 24 * 7,
    cookieOptions: {
      httpOnly: true,
      sameSite: "lax",
      secure: env.appUrl.startsWith("https://"),
      path: "/",
    },
  };
}

export async function getSession() {
  return getIronSession<SessionData>(await cookies(), options());
}

const DEV_USER: SessionUser = { sub: "dev", name: "Développement" };

/** The signed-in user, or null. */
export async function currentUser(): Promise<SessionUser | null> {
  if (env.devNoAuth) return DEV_USER;
  return (await getSession()).user ?? null;
}

/** Pages: redirects to /login when signed out. */
export async function requireUser(): Promise<SessionUser> {
  const user = await currentUser();
  if (!user) redirect("/login");
  return user;
}

/** Route handlers: a 401 response when signed out, otherwise null. */
export async function unauthorized(): Promise<Response | null> {
  return (await currentUser()) ? null : Response.json({ error: "Non authentifié" }, { status: 401 });
}
