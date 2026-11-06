"use client";

import { createPortal } from "react-dom";
import { useEffect, useRef, type ReactNode, type RefObject } from "react";
import { AnimatePresence, motion, useDragControls, type PanInfo } from "motion/react";
import { X } from "lucide-react";
import { useIsClient, useMediaQuery, useReducedMotion } from "@/hooks/use-media-query";
import { activeElement, lockScroll } from "@/lib/dom";

const EASE_OUT: [number, number, number, number] = [0.23, 1, 0.32, 1];
const EASE_DRAWER: [number, number, number, number] = [0.32, 0.72, 0, 1];
const DIALOG_WIDTH = 520;

export interface OverlayProps {
  open: boolean;
  onClose: () => void;
  /** Accessible name of the dialog. */
  label: string;
  /** Screen rect of the element that opened it: the dialog grows out of it. */
  origin?: DOMRect | null;
  /** Called once the closing animation is over. */
  onExited?: () => void;
  /** "sheet" forces the bottom sheet even on desktop. */
  variant?: "auto" | "sheet";
  children: ReactNode;
}

/**
 * Popup that adapts to the screen: a centered glass dialog that grows out of its origin on desktop,
 * a draggable bottom sheet on phones (DA Drawer). Escape, scrim tap and the close button dismiss it;
 * focus returns to the opener.
 */
export function Overlay({ open, onClose, label, origin, onExited, variant = "auto", children }: OverlayProps) {
  const isClient = useIsClient();
  const isPhone = useMediaQuery("(max-width: 639px)");
  const asSheet = variant === "sheet" || isPhone;

  if (!isClient) return null;
  return createPortal(
    <AnimatePresence onExitComplete={onExited}>
      {open && (
        <OverlayBody key="overlay" onClose={onClose} label={label} origin={origin} asSheet={asSheet}>
          {children}
        </OverlayBody>
      )}
    </AnimatePresence>,
    document.body
  );
}

function OverlayBody({ onClose, label, origin, asSheet, children }: {
  onClose: () => void;
  label: string;
  origin?: DOMRect | null;
  asSheet: boolean;
  children: ReactNode;
}) {
  const panel = useRef<HTMLDivElement>(null);
  const reduced = useReducedMotion();
  const close = useRef(onClose);
  useEffect(() => {
    close.current = onClose;
  }, [onClose]);

  // Scroll lock, Escape, focus in and back out.
  useEffect(() => {
    const opener = activeElement();
    const unlock = lockScroll();
    panel.current?.focus({ preventScroll: true });
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") close.current();
    };
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      unlock();
      opener?.focus({ preventScroll: true });
    };
  }, []);

  return (
    <div className="fixed inset-0 z-[60]">
      <motion.div
        className="scrim absolute inset-0"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: reduced ? 0 : 0.26, ease: EASE_OUT }}
        onClick={onClose}
        aria-hidden
      />
      {asSheet ? (
        <Sheet panel={panel} label={label} onClose={onClose} reduced={reduced}>
          {children}
        </Sheet>
      ) : (
        <Dialog panel={panel} label={label} origin={origin} onClose={onClose} reduced={reduced}>
          {children}
        </Dialog>
      )}
    </div>
  );
}

/** Transform that puts the centered dialog right on top of its origin rect. */
function fromOrigin(origin: DOMRect) {
  const width = Math.min(DIALOG_WIDTH, window.innerWidth - 48);
  return {
    x: origin.left + origin.width / 2 - window.innerWidth / 2,
    y: origin.top + origin.height / 2 - window.innerHeight / 2,
    scale: Math.max(0.35, Math.min(1, origin.width / width)),
    // Starts half visible so it reads as the card itself lifting off the page.
    opacity: 0.5,
  };
}

function Dialog({ panel, label, origin, onClose, reduced, children }: {
  panel: RefObject<HTMLDivElement | null>;
  label: string;
  origin?: DOMRect | null;
  onClose: () => void;
  reduced: boolean;
  children: ReactNode;
}) {
  const from = reduced ? { opacity: 0 } : origin ? fromOrigin(origin) : { opacity: 0, scale: 0.95 };
  return (
    <div className="pointer-events-none absolute inset-0 flex items-center justify-center p-6">
      <motion.div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-label={label}
        tabIndex={-1}
        className="glass-dialog pointer-events-auto relative flex max-h-[min(86dvh,760px)] w-full flex-col overflow-hidden rounded-xl outline-none"
        style={{ maxWidth: DIALOG_WIDTH }}
        initial={from}
        animate={{ x: 0, y: 0, scale: 1, opacity: 1 }}
        exit={{ ...from, transition: { duration: reduced ? 0 : 0.24, ease: EASE_OUT } }}
        transition={reduced ? { duration: 0 } : { type: "spring", duration: 0.5, bounce: 0.12, opacity: { duration: 0.16 } }}
      >
        <CloseButton onClose={onClose} />
        <div className="overflow-y-auto overscroll-contain">{children}</div>
      </motion.div>
    </div>
  );
}

function Sheet({ panel, label, onClose, reduced, children }: {
  panel: RefObject<HTMLDivElement | null>;
  label: string;
  onClose: () => void;
  reduced: boolean;
  children: ReactNode;
}) {
  const controls = useDragControls();
  const onDragEnd = (_: unknown, info: PanInfo) => {
    const height = panel.current?.offsetHeight ?? 400;
    // Projected position: the sheet keeps its momentum when released.
    if (info.offset.y + info.velocity.y * 0.2 > height / 2 || info.velocity.y > 900) onClose();
  };
  return (
    <motion.div
      ref={panel}
      role="dialog"
      aria-modal="true"
      aria-label={label}
      tabIndex={-1}
      className="glass-sheet absolute inset-x-0 bottom-0 mx-auto flex max-h-[85dvh] w-full max-w-[560px] flex-col rounded-t-[var(--radius-2xl)] border-b-0 pb-[env(safe-area-inset-bottom)] outline-none"
      initial={{ y: "100%" }}
      animate={{ y: 0 }}
      exit={{ y: "100%" }}
      transition={{ duration: reduced ? 0 : 0.5, ease: EASE_DRAWER }}
      drag="y"
      dragListener={false}
      dragControls={controls}
      dragConstraints={{ top: 0, bottom: 0 }}
      dragElastic={{ top: 0.06, bottom: 1 }}
      dragTransition={{ bounceStiffness: 500, bounceDamping: 40 }}
      onDragEnd={onDragEnd}
    >
      {/* The handle drags the sheet; the content keeps its own scroll. */}
      <div
        className="flex shrink-0 cursor-grab touch-none justify-center pt-2.5 pb-2 active:cursor-grabbing"
        onPointerDown={(event) => controls.start(event)}
      >
        <span className="h-[5px] w-10 rounded-full bg-foreground/25" />
      </div>
      <div className="overflow-y-auto overscroll-contain">{children}</div>
    </motion.div>
  );
}

function CloseButton({ onClose }: { onClose: () => void }) {
  return (
    <button
      onClick={onClose}
      className="pressable absolute top-3.5 right-3.5 z-10 flex size-8 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground"
      aria-label="Fermer"
    >
      <X className="size-4" />
    </button>
  );
}
