import { ServicesDashboard } from "@/components/services-dashboard";
import { listServices } from "@/lib/db";
import { requireUser } from "@/lib/session";

export const dynamic = "force-dynamic";

export default async function DashboardPage() {
  const user = await requireUser();
  return <ServicesDashboard services={listServices()} userName={user.name} />;
}
