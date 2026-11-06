import { listServices } from "@/lib/db";
import { unauthorized } from "@/lib/session";

/** Configured services, without their credentials. */
export async function GET() {
  const denied = await unauthorized();
  if (denied) return denied;
  return Response.json({ services: listServices() });
}
