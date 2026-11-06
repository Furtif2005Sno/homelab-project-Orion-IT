import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { LogIn } from "lucide-react";
import { Card, Logo } from "@/components/ui";
import { currentUser } from "@/lib/session";

export const metadata: Metadata = { title: "Connexion" };
export const dynamic = "force-dynamic";

const ERRORS: Record<string, string> = {
  expired: "La connexion a expiré, recommence.",
  forbidden: "Ce compte n'est pas autorisé à accéder au tableau de bord.",
  failed: "La connexion a échoué. Vérifie la configuration OIDC.",
  unavailable: "Le fournisseur d'identité est injoignable. Vérifie OIDC_ISSUER.",
};

export default async function LoginPage({ searchParams }: { searchParams: Promise<{ error?: string; returnTo?: string }> }) {
  if (await currentUser()) redirect("/");
  const { error, returnTo } = await searchParams;
  const target = returnTo?.startsWith("/") && !returnTo.startsWith("//") ? returnTo : "/";
  return (
    // The Orion IT wallpaper is kept for the sign-in screen, where it is the only content.
    <main
      className="flex min-h-dvh items-center justify-center bg-cover bg-center p-4"
      style={{ backgroundImage: "linear-gradient(rgb(10 10 10 / 0.72), rgb(10 10 10 / 0.86)), url(/wallpaper.svg)" }}
    >
      <Card className="flex w-full max-w-sm flex-col items-center gap-6 p-8 text-center">
        <Logo className="text-xl" />
        <div className="flex flex-col gap-1.5">
          <h1 className="text-[1.6rem] font-semibold">Connexion</h1>
          <p className="text-[0.93rem] text-muted-foreground">Tous tes services auto-hébergés, depuis ton navigateur.</p>
        </div>
        {error && <p className="w-full rounded-lg border border-destructive/30 bg-destructive/12 px-3 py-2 text-[0.93rem] text-destructive">{ERRORS[error] ?? ERRORS.failed}</p>}
        <a
          href={`/api/auth/login?returnTo=${encodeURIComponent(target)}`}
          className="pressable inline-flex h-[2.57rem] w-full items-center justify-center gap-2 rounded-lg border border-primary/70 bg-primary px-4 font-medium text-primary-foreground shadow-[inset_0_1px_0_rgb(255_255_255/0.16)] hover:bg-primary-hover"
        >
          <LogIn className="size-4" />
          Se connecter avec OIDC
        </a>
      </Card>
    </main>
  );
}
