/** Re-mounted on every navigation: each page fades up into place. */
export default function DashboardTemplate({ children }: { children: React.ReactNode }) {
  return <div className="page-enter">{children}</div>;
}
