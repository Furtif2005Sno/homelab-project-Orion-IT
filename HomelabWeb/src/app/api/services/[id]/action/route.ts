import { getService } from "@/lib/db";
import { ActionError, runAction } from "@/lib/services/actions";
import { ServiceHttpError } from "@/lib/services/http";
import { unauthorized } from "@/lib/session";

/** Runs one service action (same operations as the app's dashboard buttons). */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const denied = await unauthorized();
  if (denied) return denied;
  const { id } = await params;
  const service = getService(id);
  if (!service) return Response.json({ error: "Service introuvable" }, { status: 404 });
  const body = (await request.json().catch(() => null)) as { action?: unknown; params?: unknown } | null;
  const action = typeof body?.action === "string" ? body.action : "";
  const rawParams = body?.params && typeof body.params === "object" ? (body.params as Record<string, unknown>) : {};
  const actionParams = Object.fromEntries(Object.entries(rawParams).filter(([, value]) => typeof value === "string")) as Record<string, string>;
  try {
    return Response.json({ message: await runAction(service, action, actionParams) });
  } catch (error) {
    if (error instanceof ActionError) return Response.json({ error: error.message }, { status: 400 });
    if (error instanceof ServiceHttpError) {
      const message = error.status === 401 || error.status === 403 ? "Le service a refusé l'action (identifiants ou droits)." : `Le service a répondu HTTP ${error.status}.`;
      return Response.json({ error: message }, { status: 502 });
    }
    console.warn(`[action] ${service.type} ${action}:`, error instanceof Error ? error.message : error);
    return Response.json({ error: "Le service n'a pas répondu." }, { status: 502 });
  }
}
