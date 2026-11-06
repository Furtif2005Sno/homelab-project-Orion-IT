// Small DOM helpers shared by overlays, kept outside components on purpose.

let locks = 0;
let savedOverflow = "";

/** Locks page scroll while an overlay is open; nested locks are counted. Returns the unlock function. */
export function lockScroll(): () => void {
  if (locks++ === 0) {
    savedOverflow = document.documentElement.style.overflow;
    document.documentElement.style.overflow = "hidden";
  }
  let released = false;
  return () => {
    if (released) return;
    released = true;
    if (--locks === 0) document.documentElement.style.overflow = savedOverflow;
  };
}

export const activeElement = () => (document.activeElement instanceof HTMLElement ? document.activeElement : null);

/** Event used by the mobile nav to ask the dashboard to focus its search field. */
export const SEARCH_EVENT = "homelab:search";
