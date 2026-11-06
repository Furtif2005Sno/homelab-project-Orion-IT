import type { ReactNode } from "react";
import { Overline } from "@/components/ui";

/** Page header: overline, mono title, optional description and actions. Plain, no card. */
export function PageHeader({ overline, title, description, actions }: {
  overline: string;
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <Overline>{overline}</Overline>
        <h1 className="mt-1.5 truncate text-[1.6rem] leading-tight font-semibold">{title}</h1>
        {description && <p className="mt-1.5 text-[0.93rem] text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
    </header>
  );
}
