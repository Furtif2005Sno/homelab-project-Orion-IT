import type { BackupServiceEntry } from "@/lib/backup-format";
import { deleteService, findServiceId, listServices, upsertService } from "@/lib/db";
import { serviceType } from "@/lib/service-types";
import { unauthorized } from "@/lib/session";

interface ImportBody {
  /** Entries decrypted in the browser from the mobile backup file. */
  services: BackupServiceEntry[];
  /** "merge" updates matching services and adds the rest; "replace" removes everything else first. */
  mode: "merge" | "replace";
}

export async function POST(request: Request) {
  const denied = await unauthorized();
  if (denied) return denied;

  let body: ImportBody;
  try {
    body = (await request.json()) as ImportBody;
  } catch {
    return Response.json({ error: "Requête invalide" }, { status: 400 });
  }
  if (!Array.isArray(body.services)) return Response.json({ error: "Aucun service à importer" }, { status: 400 });

  if (body.mode === "replace") {
    for (const existing of listServices()) deleteService(existing.id);
  }

  let added = 0;
  let updated = 0;
  const skipped: string[] = [];
  for (const entry of body.services) {
    const type = serviceType(entry.type);
    if (!type || !entry.url) {
      skipped.push(entry.label || entry.type);
      continue;
    }
    const url = entry.url.trim();
    const label = (entry.label || type.name).trim();
    const existingId = findServiceId(type.key, url, label);
    upsertService({
      id: existingId ?? undefined,
      type: type.key,
      label,
      url,
      fallbackUrl: entry.fallbackUrl || null,
      allowSelfSigned: Boolean(entry.allowSelfSigned),
      isPreferred: Boolean(entry.isPreferred),
      secrets: {
        token: entry.token ?? null,
        proxmoxCsrfToken: entry.proxmoxCsrfToken ?? null,
        username: entry.username ?? null,
        apiKey: entry.apiKey ?? null,
        piholePassword: entry.piholePassword ?? null,
        piholeAuthMode: entry.piholeAuthMode ?? null,
        password: entry.password ?? null,
      },
    });
    if (existingId) updated++;
    else added++;
  }
  return Response.json({ added, updated, skipped });
}
