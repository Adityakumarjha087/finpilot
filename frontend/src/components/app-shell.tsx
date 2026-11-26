import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router";
import { ChevronsLeft, ChevronsRight, LogOut, Menu, Settings, Wallet } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/sheet";
import { ThemeToggle } from "@/components/theme-toggle";
import { Badge } from "@/components/ui/badge";
import { navItemsFor } from "@/components/nav";
import { useAuth } from "@/lib/auth";
import { cn } from "@/lib/utils";

const COLLAPSE_KEY = "finpilot.sidebarCollapsed";

function Brand({ collapsed = false }: { collapsed?: boolean }) {
  return (
    <div className="flex h-14 items-center gap-2 px-4 font-semibold">
      <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
        <Wallet className="size-4" aria-hidden />
      </span>
      {!collapsed && <span className="text-lg tracking-tight">FinPilot</span>}
    </div>
  );
}

function SidebarNav({ collapsed = false, onNavigate }: { collapsed?: boolean; onNavigate?: () => void }) {
  const { user } = useAuth();
  return (
    <nav aria-label="Main" className="flex-1 space-y-0.5 overflow-y-auto px-2 py-2">
      {navItemsFor(user?.role).map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.to === "/"}
          onClick={onNavigate}
          title={collapsed ? item.label : undefined}
          className={({ isActive }) =>
            cn(
              "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground",
              isActive && "bg-primary/10 text-primary hover:bg-primary/15 hover:text-primary",
              collapsed && "justify-center px-0",
            )
          }
        >
          <item.icon className="size-4 shrink-0" aria-hidden />
          {collapsed ? <span className="sr-only">{item.label}</span> : <span>{item.label}</span>}
        </NavLink>
      ))}
    </nav>
  );
}

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === "1";
  } catch {
    return false;
  }
}

export function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(readCollapsed);

  const toggleCollapsed = () => {
    setCollapsed((c) => {
      try {
        localStorage.setItem(COLLAPSE_KEY, c ? "0" : "1");
      } catch {
        /* ignore */
      }
      return !c;
    });
  };

  const onLogout = async () => {
    await logout();
    navigate("/login", { replace: true });
  };

  const initials = (user?.displayName || user?.email || "?")
    .split(/\s+/)
    .map((p) => p[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

  return (
    <div className="flex min-h-screen">
      <aside
        className={cn(
          "sticky top-0 hidden h-screen shrink-0 flex-col border-r bg-card transition-[width] md:flex",
          collapsed ? "w-16" : "w-60",
        )}
        aria-label="Sidebar"
      >
        <Brand collapsed={collapsed} />
        <SidebarNav collapsed={collapsed} />
        <div className="border-t p-2">
          <Button
            variant="ghost"
            size="sm"
            className="w-full justify-center"
            onClick={toggleCollapsed}
            aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          >
            {collapsed ? <ChevronsRight /> : <><ChevronsLeft /> Collapse</>}
          </Button>
        </div>
      </aside>

      <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
        <SheetContent aria-describedby={undefined}>
          <SheetTitle className="sr-only">Navigation</SheetTitle>
          <Brand />
          <SidebarNav onNavigate={() => setMobileOpen(false)} />
        </SheetContent>
      </Sheet>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b bg-background/85 px-4 backdrop-blur md:px-6">
          <Button variant="ghost" size="icon" className="md:hidden" aria-label="Open menu" onClick={() => setMobileOpen(true)}>
            <Menu />
          </Button>
          <div className="flex-1" />
          <ThemeToggle />
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" className="gap-2 px-2" aria-label="User menu" data-testid="user-menu">
                <span className="flex size-7 items-center justify-center rounded-full bg-primary/15 text-xs font-semibold text-primary">
                  {initials}
                </span>
                <span className="hidden max-w-40 truncate text-sm sm:inline">{user?.displayName}</span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuLabel>
                <div className="font-medium">{user?.displayName}</div>
                <div className="text-xs text-muted-foreground">{user?.email}</div>
                {user?.role === "ADMIN" && <Badge className="mt-1">Admin</Badge>}
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem onSelect={() => navigate("/settings")}>
                <Settings /> Settings
              </DropdownMenuItem>
              <DropdownMenuItem onSelect={onLogout} data-testid="logout">
                <LogOut /> Log out
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
          <Button variant="ghost" size="icon" aria-label="Log out" className="hidden sm:inline-flex" onClick={onLogout}>
            <LogOut />
          </Button>
        </header>
        <main id="main" className="mx-auto w-full max-w-7xl flex-1 p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
