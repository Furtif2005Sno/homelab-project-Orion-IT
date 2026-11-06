"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { AnimatePresence, motion } from "motion/react";
import { CheckCircle2, Loader2, MoreHorizontal, XCircle } from "lucide-react";
import { Overlay } from "@/components/overlay";
import { Button } from "@/components/ui";
import type { DashAction } from "@/lib/services/dashboards";
import { cn } from "@/lib/utils";

interface Toast {
  id: number;
  ok: boolean;
  message: string;
}

interface ActionsContext {
  run: (action: DashAction) => void;
  pending: string | null;
}

const Context = createContext<ActionsContext | null>(null);
const keyOf = (action: DashAction) => `${action.id}:${JSON.stringify(action.params ?? {})}`;

/** Runs dashboard actions for one service: confirmation when needed, result toast, then a refresh. */
export function ActionsProvider({ serviceId, onDone, children }: { serviceId: string; onDone: () => void; children: ReactNode }) {
  const [pending, setPending] = useState<string | null>(null);
  const [confirming, setConfirming] = useState<DashAction | null>(null);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(0);

  const toast = useCallback((ok: boolean, message: string) => {
    const id = ++nextId.current;
    setToasts((current) => [...current, { id, ok, message }]);
    setTimeout(() => setToasts((current) => current.filter((item) => item.id !== id)), 4200);
  }, []);

  const execute = useCallback(
    async (action: DashAction) => {
      setPending(keyOf(action));
      try {
        const response = await fetch(`/api/services/${serviceId}/action`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ action: action.id, params: action.params ?? {} }),
        });
        const body = await response.json().catch(() => ({}));
        toast(response.ok, response.ok ? (body.message ?? "Action effectuée") : (body.error ?? "Action refusée"));
        if (response.ok) onDone();
      } catch {
        toast(false, "Le serveur n'a pas répondu.");
      }
      setPending(null);
    },
    [serviceId, onDone, toast]
  );

  const run = useCallback((action: DashAction) => (action.confirm ? setConfirming(action) : void execute(action)), [execute]);

  return (
    <Context.Provider value={{ run, pending }}>
      {children}

      <Overlay open={confirming !== null} onClose={() => setConfirming(null)} label="Confirmation">
        {confirming && (
          <div className="flex flex-col gap-5 p-6">
            <div className="flex flex-col gap-1.5 pr-8">
              <h2 className="text-[1.15rem] font-semibold tracking-[-0.02em]">{confirming.label}</h2>
              <p className="text-[0.93rem] text-muted-foreground">{confirming.confirm}</p>
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setConfirming(null)}>
                Annuler
              </Button>
              <Button
                variant={confirming.tone === "destructive" ? "destructive" : "primary"}
                onClick={() => {
                  const action = confirming;
                  setConfirming(null);
                  void execute(action);
                }}
              >
                {confirming.label}
              </Button>
            </div>
          </div>
        )}
      </Overlay>

      {/* Toasts: bottom right on desktop, above the floating nav on phones. */}
      <div className="pointer-events-none fixed right-4 bottom-28 z-[70] flex w-[min(360px,calc(100%-2rem))] flex-col gap-2 lg:bottom-6" aria-live="polite">
        <AnimatePresence initial={false}>
          {toasts.map((item) => (
            <motion.div
              key={item.id}
              layout
              initial={{ opacity: 0, y: 16, scale: 0.97 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, x: 40 }}
              transition={{ duration: 0.3, ease: [0.23, 1, 0.32, 1] }}
              className="glass-dialog pointer-events-auto flex items-start gap-2.5 rounded-lg px-3.5 py-3 text-[0.93rem]"
            >
              {item.ok ? <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-success" /> : <XCircle className="mt-0.5 size-4 shrink-0 text-destructive" />}
              <span>{item.message}</span>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </Context.Provider>
  );
}

function useActions() {
  const context = useContext(Context);
  if (!context) throw new Error("ActionsProvider missing");
  return context;
}

/** Service-wide action bar. */
export function ActionBar({ actions }: { actions: DashAction[] }) {
  const { run, pending } = useActions();
  return (
    <div className="flex flex-wrap gap-2">
      {actions.map((action) => {
        const busy = pending === keyOf(action);
        return (
          <Button
            key={keyOf(action) + action.label}
            size="sm"
            variant={action.tone === "primary" ? "primary" : action.tone === "destructive" ? "outline" : "outline"}
            className={cn(action.tone === "destructive" && "text-destructive hover:bg-destructive/12")}
            onClick={() => run(action)}
            disabled={pending !== null}
          >
            {busy && <Loader2 className="animate-spin" />}
            {action.label}
          </Button>
        );
      })}
    </div>
  );
}

/** Row actions: inline buttons for one or two, a menu beyond that. */
export function RowActions({ actions }: { actions: DashAction[] }) {
  const { run, pending } = useActions();
  const [open, setOpen] = useState(false);
  const menu = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (event: PointerEvent) => {
      if (!menu.current?.contains(event.target as Node)) setOpen(false);
    };
    const escape = (event: KeyboardEvent) => event.key === "Escape" && setOpen(false);
    window.addEventListener("pointerdown", close);
    window.addEventListener("keydown", escape);
    return () => {
      window.removeEventListener("pointerdown", close);
      window.removeEventListener("keydown", escape);
    };
  }, [open]);

  const busy = actions.some((action) => pending === keyOf(action));

  if (actions.length <= 2) {
    return (
      <div className="flex shrink-0 gap-1">
        {actions.map((action) => (
          <button
            key={keyOf(action) + action.label}
            onClick={() => run(action)}
            disabled={pending !== null}
            className={cn(
              "pressable inline-flex h-7 items-center gap-1 rounded-md border border-border/70 px-2 text-[0.786rem] font-medium transition-colors disabled:opacity-50",
              action.tone === "primary" ? "border-primary/40 text-primary hover:bg-primary/12" : action.tone === "destructive" ? "text-destructive hover:bg-destructive/12" : "hover:bg-muted/60"
            )}
          >
            {pending === keyOf(action) && <Loader2 className="size-3 animate-spin" />}
            {action.label}
          </button>
        ))}
      </div>
    );
  }

  return (
    <div ref={menu} className="relative shrink-0">
      <button
        onClick={() => setOpen((value) => !value)}
        disabled={pending !== null}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Actions"
        className="pressable flex size-7 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground disabled:opacity-50"
      >
        {busy ? <Loader2 className="size-4 animate-spin" /> : <MoreHorizontal className="size-4" />}
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            role="menu"
            initial={{ opacity: 0, scale: 0.96, y: -4 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: -4 }}
            transition={{ duration: 0.18, ease: [0.23, 1, 0.32, 1] }}
            style={{ transformOrigin: "top right" }}
            className="absolute top-8 right-0 z-30 flex min-w-52 flex-col rounded-lg border border-border/70 bg-background/95 p-1 shadow-[var(--shadow-float)] backdrop-blur-2xl"
          >
            {actions.map((action) => (
              <button
                key={keyOf(action) + action.label}
                role="menuitem"
                onClick={() => {
                  setOpen(false);
                  run(action);
                }}
                className={cn("rounded-md px-2.5 py-2 text-left text-[0.857rem] transition-colors hover:bg-muted/60", action.tone === "destructive" && "text-destructive")}
              >
                {action.label}
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
