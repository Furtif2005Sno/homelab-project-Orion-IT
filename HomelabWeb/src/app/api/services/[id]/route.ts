import { deleteService } from "@/lib/db";
import { unauthorized } from "@/lib/session";

export async function DELETE(_request: Request, { params }: { params: Promise<{ id: string }> }) {
  const denied = await unauthorized();
  if (denied) return denied;
  const { id } = await params;
  return deleteService(id) ? new Response(null, { status: 204 }) : Response.json({ error: "Service introuvable" }, { status: 404 });
}
