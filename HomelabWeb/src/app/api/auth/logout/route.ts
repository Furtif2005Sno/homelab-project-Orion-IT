import { NextResponse } from "next/server";
import { env } from "@/lib/env";
import { getSession } from "@/lib/session";

/** Ends the local session (the identity provider session is left untouched). */
export async function POST() {
  const session = await getSession();
  session.destroy();
  return NextResponse.redirect(new URL("/login", env.appUrl), { status: 303 });
}
