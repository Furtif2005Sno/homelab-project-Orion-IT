"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import {
  Clapperboard,
  Code2,
  DatabaseBackup,
  Gamepad2,
  LayoutDashboard,
  LogOut,
  Menu,
  Network,
  Server,
  ShieldCheck,
  X,
  type LucideIcon,
} from "lucide-react";
import { Logo } from "@/components/ui";
import { ThemeSwitcher } from "@/components/theme-switcher";
import { CATEGORY_LABELS, type ServiceCategory } from "@/lib/service-types";
import { cn } from "@/lib/utils";

const CATEGORY_ICONS: Record<ServiceCategory, LucideIcon> = {
  infrastructure: Server,
  network: Network,
  security: ShieldCheck,
  media: Clapperboard,
  games: Gamepad2,
  development: Code2,
};

export interface SidebarProps {
  userName: string;
  userEmail?: string;
  categories: { category: ServiceCategory; count: number }[];
}

/**
 * Arcane-style sidebar: brand, grouped navigation (active item = primary at 14 % with a primary icon),
 * service categories with counts, then theme and account at the bottom.
 * Fixed on desktop; a drawer on small screens.
 */
export function Sidebar(props: SidebarProps) {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();

  return (
    <>
      {/* Mobile top bar */}
      <div className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-border/70 bg-background/80 px-4 backdrop-blur-md lg:hidden">
        <Logo />
        <button
          onClick={() => setOpen(true)}
          className="pressable rounded-md p-2 text-muted-foreground hover:bg-muted/60 hover:text-foreground"
          aria-label="Ouvrir le menu"
        >
          <Menu className="size-5" />
        </button>
      </div>

      {/* Mobile overlay */}
      <div
        className={cn("fixed inset-0 z-40 bg-black/50 backdrop-blur-[2px] transition-opacity lg:hidden", open ? "opacity-100" : "pointer-events-none opacity-0")}
        onClick={() => setOpen(false)}
        aria-hidden
      />

      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-50 flex w-[248px] flex-col border-r border-border/70 bg-sidebar transition-transform duration-200 ease-[cubic-bezier(0.23,1,0.32,1)] lg:translate-x-0",
          open ? "translate-x-0" : "-translate-x-full"
        )}
      >
        <div className="flex h-14 shrink-0 items-center justify-between px-5">
          <Link href="/" onClick={() => setOpen(false)}>
            <Logo />
          </Link>
          <button onClick={() => setOpen(false)} className="rounded-md p-1.5 text-muted-foreground hover:bg-muted/60 lg:hidden" aria-label="Fermer le menu">
            <X className="size-4" />
          </button>
        </div>

        <nav className="flex flex-1 flex-col gap-6 overflow-y-auto px-3 py-2">
          <NavGroup label="Général">
            <NavItem href="/" icon={LayoutDashboard} label="Tableau de bord" active={pathname === "/"} onNavigate={() => setOpen(false)} />
          </NavGroup>

          {props.categories.length > 0 && (
            <NavGroup label="Services">
              {props.categories.map(({ category, count }) => (
                <NavItem
                  key={category}
                  href={`/#${category}`}
                  icon={CATEGORY_ICONS[category]}
                  label={CATEGORY_LABELS[category]}
                  count={count}
                  onNavigate={() => setOpen(false)}
                />
              ))}
            </NavGroup>
          )}

          <NavGroup label="Administration">
            <NavItem href="/backup" icon={DatabaseBackup} label="Import / export" active={pathname === "/backup"} onNavigate={() => setOpen(false)} />
          </NavGroup>
        </nav>

        <div className="flex shrink-0 flex-col gap-3 border-t border-border/70 p-3">
          <ThemeSwitcher />
          <div className="flex items-center gap-2.5 rounded-lg px-1.5 py-1">
            <span className="flex size-8 shrink-0 items-center justify-center rounded-md bg-primary/14 font-mono text-[0.857rem] font-semibold text-primary">
              {props.userName.slice(0, 1).toUpperCase()}
            </span>
            <span className="min-w-0 flex-1 leading-tight">
              <span className="block truncate text-[0.93rem] font-medium">{props.userName}</span>
              {props.userEmail && <span className="block truncate text-[0.786rem] text-muted-foreground">{props.userEmail}</span>}
            </span>
            <form action="/api/auth/logout" method="post">
              <button
                type="submit"
                className="pressable rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground"
                aria-label="Se déconnecter"
                title="Se déconnecter"
              >
                <LogOut className="size-4" />
              </button>
            </form>
          </div>
        </div>
      </aside>
    </>
  );
}

function NavGroup({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <p className="px-2.5 pb-1.5 text-[0.786rem] font-medium text-muted-foreground">{label}</p>
      {children}
    </div>
  );
}

function NavItem({
  href,
  icon: Icon,
  label,
  count,
  active = false,
  onNavigate,
}: {
  href: string;
  icon: LucideIcon;
  label: string;
  count?: number;
  active?: boolean;
  onNavigate: () => void;
}) {
  return (
    <Link
      href={href}
      onClick={onNavigate}
      className={cn(
        "group flex h-9 items-center gap-2.5 rounded-md px-2.5 text-[0.93rem] font-medium transition-colors duration-150",
        active ? "bg-primary/14 text-foreground" : "text-muted-foreground hover:bg-muted/50 hover:text-foreground"
      )}
    >
      <Icon className={cn("size-4 shrink-0", active ? "text-primary" : "text-muted-foreground group-hover:text-foreground")} strokeWidth={2} />
      <span className="flex-1 truncate">{label}</span>
      {count !== undefined && <span className="font-mono text-[0.786rem] text-muted-foreground tabular">{count}</span>}
    </Link>
  );
}
