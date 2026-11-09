import { getService } from "@/lib/db";
import { buildDashboard } from "@/lib/services/dashboards";
import { describeNetworkError, networkErrorDetail, ServiceHttpError } from "@/lib/services/http";
import { unauthorized } from "@/lib/session";

/** Full dashboard data for one service (the app's dashboard screen), queried server-side. */
export async function GET(_request: Request, { params }: { params: Promise<{ id: string }> }) {
  const denied = await unauthorized();
  if (denied) return denied;
  const { id } = await params;
  const service = getService(id);
  if (!service) return Response.json({ error: "Service introuvable" }, { status: 404 });
  const headers = { "Cache-Control": "no-store" };
  try {
    return Response.json({ dashboard: await buildDashboard(service), checkedAt: new Date().toISOString() }, { headers });
  } catch (error) {
    if (error instanceof ServiceHttpError && (error.status === 401 || error.status === 403)) {
      return Response.json({ error: "Identifiants refusés par le service.", kind: "auth" }, { status: 502, headers });
    }
    console.warn(`[dashboard] ${service.type} "${service.label}":`, networkErrorDetail(error));
    const network = describeNetworkError(error);
    const message =
      error instanceof ServiceHttpError
        ? `Le service a répondu HTTP ${error.status}.`
        : network !== "Injoignable"
          ? `${network}.`
          : "Service injoignable ou réponse inattendue.";
    return Response.json({ error: message, kind: "unavailable" }, { status: 502, headers });
  }
}
