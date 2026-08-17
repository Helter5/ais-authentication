import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { Users, Ticket, LayoutDashboard, LogOut, Shield, Sliders, Settings, LayoutGrid, ScrollText, Trash2, CalendarDays } from "lucide-react";

import { cn } from "@/lib/utils";
import { useAuth } from "@/contexts/AuthContext";
import { useNavigate } from "react-router-dom";
import { adminApi } from "@/lib/api";
import { useSelectedGuildId } from "@/components/modules/shared";

export function Sidebar({ isMobile, onNavItemClick, maintenanceBannerShown }: { isMobile?: boolean, onNavItemClick?: () => void, maintenanceBannerShown?: boolean }) {
  const location = useLocation();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const guildId = useSelectedGuildId();
  const [isSuperAdmin, setIsSuperAdmin] = useState(false);
  const [canSemester, setCanSemester] = useState(false);

  useEffect(() => {
    adminApi.getAdminAccess()
      .then(result => setIsSuperAdmin(result.allowed))
      .catch(() => setIsSuperAdmin(false));
  }, []);

  useEffect(() => {
    let cancelled = false;
    if (!guildId) {
      setCanSemester(false);
      return () => { cancelled = true; };
    }
    setCanSemester(false);
    adminApi.getSemesterAccess(guildId)
      .then(r => {
        if (!cancelled) setCanSemester(r.allowed || r.reason === 'no_channel');
      })
      .catch(() => {
        if (!cancelled) setCanSemester(false);
      });
    return () => { cancelled = true; };
  }, [guildId]);

  const links = [
    { name: "Dashboard", href: "/", icon: LayoutDashboard },
    { name: "Codes", href: "/codes", icon: Ticket },
    { name: "Users Directory", href: "/users", icon: Users },
    ...(canSemester ? [{ name: "Semester", href: "/semester", icon: CalendarDays }] : []),
    { name: "Logs", href: "/access-logs", icon: ScrollText },
  ];

  const superAdminLinks = [
    { name: "Admin", href: "/admin", icon: Shield, danger: true },
    { name: "Settings", href: "/settings", icon: Settings, danger: true },
    { name: "Modules", href: "/modules", icon: LayoutGrid, danger: false },
    { name: "Commands", href: "/commands", icon: Sliders, danger: false },
    { name: "Wipe", href: "/wipe", icon: Trash2, danger: false },
  ];

  return (
    <aside
      className={cn(
        isMobile
          ? "flex h-full w-full flex-col bg-zinc-950"
          : "fixed bottom-0 left-0 z-10 hidden w-64 flex-col border-r border-zinc-800 bg-zinc-950 sm:flex"
      )}
      style={!isMobile ? { top: maintenanceBannerShown ? "44px" : "0px" } : undefined}
    >
      {!isMobile && (
        <div className="flex h-14 items-center border-b border-zinc-800 px-6 lg:h-[60px]">
          <Link to="/" className="flex items-center gap-2 font-semibold text-zinc-100">
            <LayoutDashboard className="h-6 w-6 text-indigo-400" />
            <span>Discord Auth</span>
          </Link>
        </div>
      )}
      <div className="flex-1 overflow-auto scrollbar-thin py-4">
        <nav className="grid items-start px-4 text-xs sm:text-sm font-medium gap-1">
          {links.map((link) => {
            const isActive = location.pathname === link.href ||
              (link.href === "/semester" && location.pathname.startsWith("/semester"));
            const Icon = link.icon;
            return (
              <Link
                key={link.name}
                to={link.href}
                className={cn(
                  "flex items-center gap-3 rounded-lg px-3 py-2 transition-all",
                  isActive
                    ? "bg-zinc-800 text-zinc-100 font-semibold"
                    : "text-zinc-400 hover:bg-zinc-800/60 hover:text-zinc-100"
                )}
                onClick={onNavItemClick}
              >
                <Icon className="h-5 w-5" />
                {link.name}
              </Link>
            );
          })}
        </nav>
      </div>
      <div className="border-t border-zinc-800 px-4 py-3 space-y-1">
        {isSuperAdmin && superAdminLinks.map(link => {
          const Icon = link.icon;
          const isActive = location.pathname === link.href
            || (link.href === "/modules" && location.pathname.startsWith("/modules/"))
            || (link.href === "/commands" && location.pathname.startsWith("/commands/"));

          return (
            <Link
              key={link.name}
              to={link.href}
              className={cn(
                "flex items-center gap-3 rounded-lg px-3 py-2 transition-all text-xs sm:text-sm font-medium",
                isActive
                  ? link.danger
                    ? "bg-rose-500/20 text-rose-300 font-semibold"
                    : "bg-zinc-800 text-zinc-100 font-semibold"
                  : link.danger
                    ? "bg-rose-500/10 text-rose-400 hover:bg-rose-500/20 hover:text-rose-300"
                    : "text-zinc-400 hover:bg-zinc-800/60 hover:text-zinc-100"
              )}
              onClick={onNavItemClick}
            >
              <Icon className="h-5 w-5" />
              {link.name}
            </Link>
          );
        })}
      </div>
      {isMobile && (
        <div className="p-4 border-t border-zinc-800">
          <button
            onClick={() => { logout(); navigate("/login"); if (onNavItemClick) onNavItemClick(); }}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-red-400 transition-all hover:bg-red-500/10 hover:text-red-300"
          >
            <LogOut className="h-5 w-5" />
            Logout
          </button>
        </div>
      )}
    </aside>
  );
}
