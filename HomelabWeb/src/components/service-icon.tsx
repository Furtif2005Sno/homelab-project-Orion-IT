"use client";

import { useState } from "react";
import type { ServiceTypeInfo } from "@/lib/service-types";
import { cn } from "@/lib/utils";

/** Official service logo on a tint of its brand color; falls back to the initial when it can't load. */
export function ServiceIcon({ type, label, size = "md" }: { type?: ServiceTypeInfo; label: string; size?: "md" | "lg" }) {
  const [failed, setFailed] = useState(false);
  const color = type?.color ?? "#a1a1a1";
  return (
    <span
      className={cn(
        "flex shrink-0 items-center justify-center ring-1 ring-inset ring-foreground/5",
        size === "md" ? "size-10 rounded-lg" : "size-14 rounded-xl"
      )}
      style={{ backgroundColor: `color-mix(in oklab, ${color} 12%, transparent)` }}
    >
      {type && !failed ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={type.icon}
          alt=""
          className={cn("object-contain", size === "md" ? "size-6" : "size-8")}
          loading="lazy"
          onError={() => setFailed(true)}
          // An image that already failed before hydration never fires onError: check on mount.
          ref={(element) => {
            if (element?.complete && element.naturalWidth === 0) setFailed(true);
          }}
        />
      ) : (
        <span className={cn("font-semibold", size === "md" ? "text-[0.93rem]" : "text-[1.2rem]")} style={{ color }}>
          {label.slice(0, 1).toUpperCase()}
        </span>
      )}
    </span>
  );
}
