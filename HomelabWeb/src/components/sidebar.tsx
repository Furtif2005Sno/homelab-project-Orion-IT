"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { motion, type PanInfo } from "motion/react";
import {
  BellRing,
  Clapperboard,
  Code2,
  DatabaseBackup,
  Gamepad2,
  LayoutDashboard,
  LayoutGrid,
  LogOut,
  Network,
  Search,
  Server,
  ShieldCheck,
  type LucideIcon,
} from "lucide-react";
import { Logo, Overline } from "@/components/ui";
import { Overlay } from "@/components/overlay";
import { ThemeSwitcher } from "@/components/theme-switcher";
import { SEARCH_EVENT } from "@/lib/dom";
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

const SPRING = { type: "spring", duration: 0.4, bounce: 0.15 } as const;

export interface SidebarProps {
  userName: string;
  userEmail?: string;
  categories: { category: ServiceCategory; count: number }[];
}

/** Desktop sidebar plus, on phones, Arcane's floating pill nav and its menu drawer. */
export function Sidebar(props: SidebarProps) {
  return (
    <>
      <DesktopSidebar {...props} />
      <MobileNav {...props} />
    </>
  );
}

// ---- Desktop ------------------------------------------------------------------------------

function DesktopSidebar(props: SidebarProps) {
  const pathname = usePathname();
  return (
    <aside className="fixed inset-y-0 left-0 z-40 hidden w-[232px] flex-col border-r border-border/70 bg-sidebar/70 backdrop-blur-md lg:flex">
      <div className="flex h-16 shrink-0 items-center px-5">
        <Link href="/" className="pressable">
          <Logo />
        </Link>
      </div>
      <nav className="flex flex-1 flex-col gap-6 overflow-y-auto px-3 py-2">
        <NavLinks {...props} pathname={pathname} layoutId="sidebar-active" />
      </nav>
      <div className="flex shrink-0 flex-col gap-3 border-t border-border/70 p-3">
        <ThemeSwitcher />
        <Account {...props} />
      </div>
    </aside>
  );
}

function NavLinks({ categories, pathname, layoutId, onNavigate }: SidebarProps & { pathname: string; layoutId: string; onNavigate?: () => void }) {
  return (
    <>
      <NavGroup label="Général">
        <NavItem href="/" icon={LayoutDashboard} label="Tableau de bord" active={pathname === "/"} layoutId={layoutId} onNavigate={onNavigate} />
      </NavGroup>
      {categories.length > 0 && (
        <NavGroup label="Services">
          {categories.map(({ category, count }) => (
            <NavItem
              key={category}
              href={`/#${category}`}
              icon={CATEGORY_ICONS[category]}
              label={CATEGORY_LABELS[category]}
              count={count}
              layoutId={layoutId}
              onNavigate={onNavigate}
            />
          ))}
        </NavGroup>
      )}
      <NavGroup label="Administration">
        <NavItem href="/backup" icon={DatabaseBackup} label="Import / export" active={pathname === "/backup"} layoutId={layoutId} onNavigate={onNavigate} />
      </NavGroup>
    </>
  );
}

function NavGroup({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <Overline className="px-2.5 pb-1.5">{label}</Overline>
      {children}
    </div>
  );
}

function NavItem({ href, icon: Icon, label, count, active = false, layoutId, onNavigate }: {
  href: string;
  icon: LucideIcon;
  label: string;
  count?: number;
  active?: boolean;
  layoutId: string;
  onNavigate?: () => void;
}) {
  return (
    <Link
      href={href}
      onClick={onNavigate}
      aria-current={active ? "page" : undefined}
      className={cn(
        "group relative flex h-9 items-center gap-2.5 rounded-md px-2.5 text-[0.93rem] font-medium transition-colors duration-150",
        active ? "text-foreground" : "text-muted-foreground hover:bg-muted/50 hover:text-foreground"
      )}
    >
      {/* The active background slides from one item to the next. */}
      {active && <motion.span layoutId={layoutId} className="absolute inset-0 rounded-md bg-primary/14" transition={SPRING} />}
      <Icon
        className={cn("relative size-4 shrink-0 transition-colors", active ? "text-primary" : "text-muted-foreground group-hover:text-foreground")}
        strokeWidth={2}
      />
      <span className="relative flex-1 truncate">{label}</span>
      {count !== undefined && <span className="data relative text-[0.786rem] text-muted-foreground">{count}</span>}
    </Link>
  );
}

function Account({ userName, userEmail }: SidebarProps) {
  return (
    <div className="flex items-center gap-2.5 rounded-lg px-1.5 py-1">
      <span className="flex size-8 shrink-0 items-center justify-center rounded-md bg-primary/14 text-[0.857rem] font-semibold text-primary">
        {userName.slice(0, 1).toUpperCase()}
      </span>
      <span className="min-w-0 flex-1 leading-tight">
        <span className="block truncate text-[0.93rem] font-medium">{userName}</span>
        {userEmail && <span className="block truncate text-[0.786rem] text-muted-foreground">{userEmail}</span>}
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
  );
}

// ---- Mobile -------------------------------------------------------------------------------

/**
 * Floating pill nav (DA MobileNav): hides when scrolling down past 80px, comes back when scrolling up
 * or near the top; the round primary button, or swiping the pill up, opens the menu drawer.
 */
function MobileNav(props: SidebarProps) {
  const pathname = usePathname();
  const params = useSearchParams();
  const router = useRouter();
  const [menuOpen, setMenuOpen] = useState(false);
  const [hidden, setHidden] = useState(false);
  const lastY = useRef(0);

  useEffect(() => {
    const onScroll = () => {
      const y = window.scrollY;
      const delta = y - lastY.current;
      if (y < 80) setHidden(false);
      else if (delta > 6) setHidden(true);
      else if (delta < -6) setHidden(false);
      lastY.current = y;
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const view = params.get("vue");
  const onHome = pathname === "/";
  const search = () => {
    if (onHome) window.dispatchEvent(new Event(SEARCH_EVENT));
    else router.push("/?recherche=1");
  };
  const onPanEnd = (_: unknown, info: PanInfo) => {
    if (info.offset.y < -20 && info.velocity.y < -100) setMenuOpen(true);
  };

  return (
    <>
      <motion.nav
        aria-label="Navigation"
        className="glass-float fixed bottom-[calc(0.9rem+env(safe-area-inset-bottom))] left-1/2 z-50 flex w-[calc(100%-1rem)] max-w-[28rem] items-center justify-between gap-1.5 rounded-[var(--radius-2xl)] px-2 py-1.5 lg:hidden"
        style={{ x: "-50%", touchAction: "pan-x pinch-zoom" }}
        initial={false}
        animate={hidden && !menuOpen ? { y: "calc(100% + 24px)", scale: 0.95, opacity: 0 } : { y: 0, scale: 1, opacity: 1 }}
        transition={{ duration: 0.3, ease: "easeOut" }}
        onPanEnd={onPanEnd}
      >
        <PillItem href="/" icon={LayoutGrid} label="Services" active={onHome && view !== "alertes"} />
        <PillItem href="/?vue=alertes" icon={BellRing} label="Alertes" active={onHome && view === "alertes"} />
        <motion.button
          onClick={() => setMenuOpen(true)}
          whileTap={{ scale: 0.95 }}
          className="grid size-14 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground shadow-[0_6px_18px_-6px_var(--primary)]"
          aria-label="Ouvrir le menu"
        >
          <LayoutDashboard className="size-6" />
        </motion.button>
        <PillItem href="/backup" icon={DatabaseBackup} label="Sauvegardes" active={pathname === "/backup"} />
        <PillItem icon={Search} label="Recherche" onClick={search} />
      </motion.nav>

      <Overlay open={menuOpen} onClose={() => setMenuOpen(false)} label="Menu" variant="sheet">
        <div className="flex flex-col gap-6 px-4 pt-1 pb-6">
          <div className="flex items-center justify-between px-1">
            <Logo />
          </div>
          <nav className="flex flex-col gap-5">
            <NavLinks {...props} pathname={pathname} layoutId="menu-active" onNavigate={() => setMenuOpen(false)} />
          </nav>
          <div className="flex flex-col gap-3 border-t border-border/70 pt-4">
            <ThemeSwitcher />
            <Account {...props} />
          </div>
        </div>
      </Overlay>
    </>
  );
}

function PillItem({ href, icon: Icon, label, active = false, onClick }: {
  href?: string;
  icon: LucideIcon;
  label: string;
  active?: boolean;
  onClick?: () => void;
}) {
  const inner = (
    <>
      {active && <motion.span layoutId="pill-active" className="absolute inset-0 rounded-2xl bg-muted shadow-xs" transition={SPRING} />}
      <Icon className={cn("relative size-5 transition-colors", active ? "text-primary" : "text-foreground")} strokeWidth={2} />
      <span className={cn("relative text-[0.66rem] leading-none", active ? "text-foreground" : "text-muted-foreground")}>{label}</span>
    </>
  );
  const className =
    "relative flex h-[3.1rem] min-w-11 flex-1 touch-manipulation flex-col items-center justify-center gap-1 rounded-2xl transition-transform duration-200 active:scale-[0.98]";
  return href ? (
    <Link href={href} className={className} aria-current={active ? "page" : undefined} scroll={!href.includes("?")}>
      {inner}
    </Link>
  ) : (
    <button type="button" onClick={onClick} className={className}>
      {inner}
    </button>
  );
}
