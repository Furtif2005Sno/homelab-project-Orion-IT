import { getService } from "@/lib/db";
import { summarize } from "@/lib/services/summaries";
import { unauthorized } from "@/lib/session";

/** Live status and headline numbers for one service, queried server-side. */
export async function GET(_request: Request, { params }: { params: Promise<{ id: string }> }) {
  const denied = await unauthorized();
  if (denied) return denied;
  const { id } = await params;
  const service = getService(id);
  if (!service) return Response.json({ error: "Service introuvable" }, { status: 404 });
  return Response.json(await summarize(service), { headers: { "Cache-Control": "no-store" } });
}
