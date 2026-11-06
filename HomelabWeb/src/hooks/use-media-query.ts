"use client";

import { useCallback, useSyncExternalStore } from "react";

/** Live `matchMedia` result; `false` during server rendering. */
export function useMediaQuery(query: string): boolean {
  const subscribe = useCallback(
    (onChange: () => void) => {
      const media = window.matchMedia(query);
      media.addEventListener("change", onChange);
      return () => media.removeEventListener("change", onChange);
    },
    [query]
  );
  return useSyncExternalStore(subscribe, () => window.matchMedia(query).matches, () => false);
}

const noopSubscribe = () => () => {};

/** True once rendering on the client (portals need `document`). */
export function useIsClient(): boolean {
  return useSyncExternalStore(noopSubscribe, () => true, () => false);
}

const REDUCED = "(prefers-reduced-motion: reduce)";

function subscribeMotion(onChange: () => void) {
  const media = window.matchMedia(REDUCED);
  const observer = new MutationObserver(onChange);
  media.addEventListener("change", onChange);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-motion"] });
  return () => {
    media.removeEventListener("change", onChange);
    observer.disconnect();
  };
}

/** Reduced motion from the OS, unless the viewer chose "Animations: Toujours" (<html data-motion="on">). */
export function useReducedMotion(): boolean {
  return useSyncExternalStore(
    subscribeMotion,
    () => window.matchMedia(REDUCED).matches && document.documentElement.getAttribute("data-motion") !== "on",
    () => false
  );
}
