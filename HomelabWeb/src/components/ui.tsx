import type { ButtonHTMLAttributes, HTMLAttributes, InputHTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils";

// Orion IT components (design system READMEs): Button, Badge, Card, Input.

type ButtonVariant = "primary" | "outline" | "secondary" | "ghost" | "destructive";

const buttonVariants: Record<ButtonVariant, string> = {
  primary:
    "bg-primary text-primary-foreground border border-primary/70 shadow-[inset_0_1px_0_rgb(255_255_255/0.16),0_10px_24px_-14px_rgb(15_23_42/0.35)] hover:bg-primary-hover",
  outline: "border border-border bg-surface/60 backdrop-blur-sm text-foreground hover:bg-muted/60",
  secondary: "border border-border/70 bg-secondary text-foreground hover:bg-secondary/85",
  ghost: "border border-transparent text-foreground hover:bg-muted/50",
  destructive: "border border-destructive/70 bg-destructive/85 text-white hover:bg-destructive",
};

export function Button({
  variant = "primary",
  size = "default",
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; size?: "default" | "sm" | "icon" }) {
  return (
    <button
      className={cn(
        // Height 2.57rem (sm 2.28rem), radius-lg (sm radius-md), label in sentence case, press scale 0.97.
        "pressable inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap font-medium text-[0.93rem] transition-colors duration-150 disabled:pointer-events-none disabled:opacity-55 disabled:shadow-none [&_svg]:size-4 [&_svg]:shrink-0",
        size === "default" && "h-[2.57rem] rounded-lg px-4",
        size === "sm" && "h-[2.28rem] rounded-md px-3",
        size === "icon" && "size-[2.57rem] rounded-lg",
        buttonVariants[variant],
        className
      )}
      {...props}
    />
  );
}

export type Tone = "success" | "warning" | "destructive" | "info" | "primary" | "neutral";

const toneClasses: Record<Tone, string> = {
  success: "text-success bg-success/12 border-success/30",
  warning: "text-warning bg-warning/12 border-warning/30",
  destructive: "text-destructive bg-destructive/12 border-destructive/30",
  info: "text-info bg-info/12 border-info/30",
  primary: "text-primary bg-primary/12 border-primary/30",
  neutral: "text-muted-foreground bg-muted/50 border-border",
};

/** Status pill: radius-pill, 12 % fill, 30 % border, full-color label; the dot pulses only for live states. */
export function Badge({ tone = "neutral", dot = false, pulse = false, className, children }: {
  tone?: Tone;
  dot?: boolean;
  pulse?: boolean;
  className?: string;
  children: ReactNode;
}) {
  return (
    <span className={cn("inline-flex h-[1.6rem] items-center gap-1.5 rounded-full border px-2.5 text-[0.857rem] font-semibold whitespace-nowrap", toneClasses[tone], className)}>
      {dot && (
        <span className="relative flex size-1.5">
          {pulse && <span className="absolute inline-flex size-full animate-ping rounded-full bg-current opacity-60" />}
          <span className="relative inline-flex size-1.5 rounded-full bg-current" />
        </span>
      )}
      {children}
    </span>
  );
}

export function Card({ interactive = false, className, ...props }: HTMLAttributes<HTMLDivElement> & { interactive?: boolean }) {
  return <div className={cn("glass-card", interactive && "interactive", className)} {...props} />;
}

/** Text field: sober, with a sharp orange focus. */
export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={cn(
        "h-[2.57rem] w-full rounded-lg border border-input bg-background/40 px-3 text-[1rem] text-foreground placeholder:text-muted-foreground outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/30",
        className
      )}
      {...props}
    />
  );
}

/** DA `eyebrow`: small uppercase tracked label in muted foreground (the only uppercase allowed). */
export function Overline({ className, children }: { className?: string; children: ReactNode }) {
  return <p className={cn("text-[0.72rem] leading-[1.2] font-semibold tracking-[0.08em] uppercase text-muted-foreground", className)}>{children}</p>;
}

/** Typographic logo (no logo file in the system): square primary pill + wordmark. */
export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-2.5", className)}>
      <span className="size-6 rounded-md bg-primary shadow-[inset_0_1px_0_rgb(255_255_255/0.25)]" />
      <span className="font-sans text-lg font-extrabold tracking-[-0.03em]">
        HOME<span className="text-primary">LAB</span>
      </span>
    </span>
  );
}
