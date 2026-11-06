import { Sidebar } from "@/components/sidebar";
import { listServices } from "@/lib/db";
import { serviceType, type ServiceCategory } from "@/lib/service-types";
import { requireUser } from "@/lib/session";

const CATEGORY_ORDER: ServiceCategory[] = ["infrastructure", "network", "security", "media", "games", "development"];

export default async function DashboardLayout({ children }: { children: React.ReactNode }) {
  const user = await requireUser();
  const counts = new Map<ServiceCategory, number>();
  for (const service of listServices()) {
    const category = serviceType(service.type)?.category ?? "infrastructure";
    counts.set(category, (counts.get(category) ?? 0) + 1);
  }
  const categories = CATEGORY_ORDER.filter((category) => counts.has(category)).map((category) => ({ category, count: counts.get(category)! }));

  return (
    <div className="min-h-dvh">
      <Sidebar userName={user.name} userEmail={user.email} categories={categories} />
      <main className="lg:pl-[248px]">
        <div className="mx-auto w-full max-w-[1320px] px-4 py-6 sm:px-6 lg:px-10 lg:py-10">{children}</div>
      </main>
    </div>
  );
}
