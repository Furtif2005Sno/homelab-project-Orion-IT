import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ServiceDashboardView } from "@/components/service-dashboard-view";
import { getService } from "@/lib/db";
import { requireUser } from "@/lib/session";

export const dynamic = "force-dynamic";

export async function generateMetadata({ params }: { params: Promise<{ id: string }> }): Promise<Metadata> {
  const { id } = await params;
  return { title: getService(id)?.label ?? "Service" };
}

export default async function ServicePage({ params }: { params: Promise<{ id: string }> }) {
  await requireUser();
  const { id } = await params;
  const service = getService(id);
  if (!service) notFound();
  // Credentials stay on the server: only the public record goes to the browser.
  const { secrets: _secrets, ...record } = service;
  void _secrets;
  return <ServiceDashboardView service={record} />;
}
