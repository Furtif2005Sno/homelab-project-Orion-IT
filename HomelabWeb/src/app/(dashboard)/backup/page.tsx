import type { Metadata } from "next";
import { BackupExportPanel, BackupImportPanel } from "@/components/backup-panels";
import { PageHeader } from "@/components/page-header";
import { listServices } from "@/lib/db";
import { requireUser } from "@/lib/session";

export const metadata: Metadata = { title: "Import / export" };
export const dynamic = "force-dynamic";

export default async function BackupPage() {
  await requireUser();
  const count = listServices().length;
  return (
    <div className="flex flex-col gap-8">
      <PageHeader
        overline="Administration"
        title="Import / export"
        description="Même format chiffré que l'app mobile : passe tes services d'un appareil à l'autre."
      />
      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[3fr_2fr]">
        <BackupImportPanel />
        <BackupExportPanel serviceCount={count} />
      </div>
    </div>
  );
}
