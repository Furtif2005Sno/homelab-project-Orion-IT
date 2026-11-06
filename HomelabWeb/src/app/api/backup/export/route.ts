import { encryptBackup, type BackupEnvelope } from "@/lib/backup-format";
import { listServicesWithSecrets } from "@/lib/db";
import { unauthorized } from "@/lib/session";

/** Encrypted backup in the mobile app's format, importable from the app's Backup screen. */
export async function POST(request: Request) {
  const denied = await unauthorized();
  if (denied) return denied;

  const { password } = (await request.json().catch(() => ({}))) as { password?: string };
  if (!password || password.length < 8) {
    return Response.json({ error: "Le mot de passe doit faire au moins 8 caractères." }, { status: 400 });
  }

  const envelope: BackupEnvelope = {
    version: 1,
    exportedAt: new Date().toISOString(),
    appVersion: "homelab-web",
    services: listServicesWithSecrets().map((service) => ({
      type: service.type,
      label: service.label,
      url: service.url,
      fallbackUrl: service.fallbackUrl,
      allowSelfSigned: service.allowSelfSigned,
      isPreferred: service.isPreferred,
      ...service.secrets,
    })),
  };
  const file = await encryptBackup(envelope, password);
  const date = new Date().toISOString().slice(0, 10);
  return new Response(file as BodyInit, {
    headers: {
      "Content-Type": "application/octet-stream",
      "Content-Disposition": `attachment; filename="homelab_backup_${date}.homelab"`,
      "Cache-Control": "no-store",
    },
  });
}
