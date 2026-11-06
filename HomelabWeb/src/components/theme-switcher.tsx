"use client";

import { useId, useSyncExternalStore } from "react";
import { motion } from "motion/react";
import { cn } from "@/lib/utils";

type Theme = "dark" | "light" | "oled";
type Motion = "system" | "on";

const THEMES: { value: Theme; label: string }[] = [
  { value: "dark", label: "Sombre" },
  { value: "oled", label: "OLED" },
  { value: "light", label: "Clair" },
];
const MOTIONS: { value: Motion; label: string }[] = [
  { value: "system", label: "Système" },
  { value: "on", label: "Toujours" },
];

// Both preferences live on <html> (data-theme, data-motion), set before paint by the root layout script.
function subscribe(onChange: () => void) {
  const observer = new MutationObserver(onChange);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme", "data-motion"] });
  return () => observer.disconnect();
}

function readTheme(): Theme {
  const value = document.documentElement.getAttribute("data-theme");
  return value === "light" || value === "oled" ? value : "dark";
}

function readMotion(): Motion {
  return document.documentElement.getAttribute("data-motion") === "on" ? "on" : "system";
}

/** Sets an <html> preference attribute and remembers it for this browser. */
function applyPreference(attribute: "data-theme" | "data-motion", storageKey: string, value: string, defaultValue: string) {
  const root = document.documentElement;
  if (value === defaultValue) root.removeAttribute(attribute);
  else root.setAttribute(attribute, value);
  try {
    localStorage.setItem(storageKey, value);
  } catch {
    // Private mode or blocked storage: the choice simply isn't remembered.
  }
}

/** Per-viewer display preferences: theme (dark by default) and animations (follow the OS by default). */
export function ThemeSwitcher() {
  const theme = useSyncExternalStore(subscribe, readTheme, () => "dark" as Theme);
  const motionPref = useSyncExternalStore(subscribe, readMotion, () => "system" as Motion);

  return (
    <div className="flex flex-col gap-2">
      <Segmented label="Thème" options={THEMES} value={theme} onChange={(value) => applyPreference("data-theme", "homelab-theme", value, "dark")} />
      <div className="flex items-center gap-2">
        <span className="shrink-0 pl-1 text-[0.786rem] text-muted-foreground">Animations</span>
        <Segmented
          label="Animations"
          options={MOTIONS}
          value={motionPref}
          onChange={(value) => applyPreference("data-motion", "homelab-motion", value, "system")}
          className="flex-1"
        />
      </div>
    </div>
  );
}

function Segmented<T extends string>({ label, options, value, onChange, className }: {
  label: string;
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  className?: string;
}) {
  const id = useId();
  return (
    <div className={cn("flex rounded-lg bg-muted/60 p-1", className)} role="radiogroup" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          role="radio"
          aria-checked={value === option.value}
          onClick={() => onChange(option.value)}
          className={cn(
            "relative flex-1 rounded-md px-2 py-1 text-[0.857rem] font-medium transition-colors",
            value === option.value ? "text-foreground" : "text-muted-foreground hover:text-foreground"
          )}
        >
          {value === option.value && (
            <motion.span layoutId={`${id}-pill`} className="absolute inset-0 rounded-md bg-surface shadow-sm" transition={{ type: "spring", duration: 0.25, bounce: 0.1 }} />
          )}
          <span className="relative">{option.label}</span>
        </button>
      ))}
    </div>
  );
}
