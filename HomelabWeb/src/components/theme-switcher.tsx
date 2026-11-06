"use client";

import { useSyncExternalStore } from "react";
import { cn } from "@/lib/utils";

type Theme = "dark" | "light" | "oled";
const THEMES: { value: Theme; label: string }[] = [
  { value: "dark", label: "Sombre" },
  { value: "oled", label: "OLED" },
  { value: "light", label: "Clair" },
];

/** The theme lives on <html data-theme> (set before paint by the root layout script). */
function readTheme(): Theme {
  const value = document.documentElement.getAttribute("data-theme");
  return value === "light" || value === "oled" ? value : "dark";
}

function subscribe(onChange: () => void) {
  const observer = new MutationObserver(onChange);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
  return () => observer.disconnect();
}

/** Sets the theme on <html> and remembers it for this browser. */
function applyTheme(theme: Theme) {
  const root = document.documentElement;
  if (theme === "dark") root.removeAttribute("data-theme");
  else root.setAttribute("data-theme", theme);
  try {
    localStorage.setItem("homelab-theme", theme);
  } catch {
    // Private mode or blocked storage: the choice simply isn't remembered.
  }
}

/** Per-viewer theme preference, kept in localStorage (dark by default). */
export function ThemeSwitcher() {
  const theme = useSyncExternalStore(subscribe, readTheme, () => "dark" as Theme);

  return (
    <div className="flex rounded-lg bg-muted/60 p-1" role="radiogroup" aria-label="Thème">
      {THEMES.map((option) => (
        <button
          key={option.value}
          role="radio"
          aria-checked={theme === option.value}
          onClick={() => applyTheme(option.value)}
          className={cn(
            "pressable flex-1 rounded-md px-2 py-1 text-[0.857rem] font-medium transition-colors",
            theme === option.value ? "bg-surface text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
