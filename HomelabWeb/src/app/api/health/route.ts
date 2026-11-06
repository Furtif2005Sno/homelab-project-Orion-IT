/** Liveness probe for the Docker healthcheck (no auth, no data). */
export function GET() {
  return Response.json({ status: "ok" });
}
