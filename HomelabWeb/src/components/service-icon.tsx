"use client";

import { useState } from "react";
import { cn } from "@/lib/utils";

/**
 * Official service logo (same selfh.st URLs as the app) on a tint of its brand color.
 * Falls back to the initial only when the image really fails to load.
 */
export function ServiceIcon({ icon, color, label, size = "md" }: { icon?: string; color?: string; label: string; size?: "md" | "lg" }) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const tint = color ?? "#a1a1a1";
  const failed = !icon || failedSrc === icon;
  return (
    <span
      className={cn(
        "flex shrink-0 items-center justify-center ring-1 ring-inset ring-foreground/5",
        size === "md" ? "size-10 rounded-lg" : "size-14 rounded-xl"
      )}
      style={{ backgroundColor: `color-mix(in oklab, ${tint} 12%, transparent)` }}
    >
      {!failed ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          key={icon}
          src={icon}
          alt=""
          decoding="async"
          className={cn("object-contain", size === "md" ? "size-6" : "size-8")}
          onError={() => setFailedSrc(icon)}
          // An image that failed before hydration never fires onError. Eager images only report
          // complete + naturalWidth 0 once they have actually failed (lazy ones do it before loading).
          ref={(element) => {
            if (element && element.complete && element.naturalWidth === 0 && element.currentSrc) setFailedSrc(icon);
          }}
        />
      ) : (
        <span className={cn("font-semibold", size === "md" ? "text-[0.93rem]" : "text-[1.2rem]")} style={{ color: tint }}>
          {label.slice(0, 1).toUpperCase()}
        </span>
      )}
    </span>
  );
}
