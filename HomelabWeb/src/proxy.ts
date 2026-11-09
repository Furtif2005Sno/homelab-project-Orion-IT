import { NextResponse, type NextRequest } from "next/server";

// Optimistic check only: signed-out visitors go to /login. Every page and API route still
// verifies the session itself, since a cookie's presence proves nothing.
export function proxy(request: NextRequest) {
  if (process.env.NODE_ENV !== "production" && process.env.HOMELAB_DEV_NO_AUTH === "1") {
    return NextResponse.next();
  }
  if (request.cookies.has("homelab_session")) return NextResponse.next();
  if (request.nextUrl.pathname.startsWith("/api/")) {
    return NextResponse.json({ error: "Non authentifié" }, { status: 401 });
  }
  const login = new URL("/login", request.url);
  login.searchParams.set("returnTo", request.nextUrl.pathname);
  return NextResponse.redirect(login);
}

export const config = {
  // Everything except the login page, the auth flow, the health check and static assets.
  matcher: ["/((?!login|api/auth|api/health|_next/static|_next/image|favicon.ico|logos/|wallpaper.svg).*)"],
};
