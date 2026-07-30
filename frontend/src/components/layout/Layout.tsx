import { Outlet, useNavigate } from "react-router-dom";
import { Sidebar } from "./Sidebar";
import { useEffect, useRef, useState } from "react";
import { Menu, X, LayoutDashboard, LogOut, ChevronDown, TriangleAlert } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "@/contexts/AuthContext";
import { adminApi } from "@/lib/api";
import { cn } from "@/lib/utils";
import { setSelectedGuildId } from "@/components/modules/shared";

const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:3001/api').replace(/\/$/, '');
const GUILD_KEY = "selected_guild_id";
const AVATAR_COLORS = ["#6366f1","#8b5cf6","#ec4899","#ef4444","#f97316","#22c55e","#06b6d4","#3b82f6"];

function UserAvatar({ userId, username, avatarHash }: { userId: string; username: string; avatarHash?: string | null }) {
  if (avatarHash) {
    return (
      <img
        src={`https://cdn.discordapp.com/avatars/${userId}/${avatarHash}.png?size=64`}
        alt={username}
        className="w-7 h-7 rounded-full flex-shrink-0 object-cover"
      />
    );
  }
  const color = AVATAR_COLORS[username.charCodeAt(0) % AVATAR_COLORS.length];
  return (
    <div className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold text-white flex-shrink-0"
      style={{ background: color }}>
      {username[0].toUpperCase()}
    </div>
  );
}

type Guild = { id: string; name: string; icon: string | null };

function ServerIcon({ guild, size = 6 }: { guild: Guild; size?: number }) {
  const sz = `w-${size} h-${size}`;
  if (guild.icon) {
    return <img src={guild.icon} alt={guild.name} className={`${sz} rounded-full object-cover flex-shrink-0`} />;
  }
  const initials = guild.name.split(" ").map(w => w[0]).join("").slice(0, 2).toUpperCase();
  const color = AVATAR_COLORS[guild.name.charCodeAt(0) % AVATAR_COLORS.length];
  return (
    <div className={`${sz} rounded-full flex items-center justify-center text-[10px] font-bold text-white flex-shrink-0`}
      style={{ background: color }}>
      {initials}
    </div>
  );
}

function ServerSwitcher() {
  const [guilds, setGuilds] = useState<Guild[]>([]);
  const [current, setCurrent] = useState<Guild | null>(null);
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    adminApi.getDiscordGuilds().then(gs => {
      setGuilds(gs);
      const savedId = localStorage.getItem(GUILD_KEY);
      const found = gs.find(g => g.id === savedId) ?? gs[0] ?? null;
      setCurrent(found);
    }).catch(() => {});
  }, []);

  useEffect(() => {
    function h(e: MouseEvent) { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); }
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  const select = (guild: Guild) => {
    setSelectedGuildId(guild.id);
    setCurrent(guild);
    setOpen(false);
  };

  if (!current) return null;

  return (
    <div ref={ref} className="relative">
      <button onClick={() => setOpen(o => !o)}
        className="flex items-center gap-2 px-2.5 py-1.5 rounded-lg bg-zinc-800/60 border border-zinc-700/60 hover:border-zinc-600 hover:bg-zinc-800 transition-all">
        <ServerIcon guild={current} size={5} />
        <span className="text-sm text-zinc-200 font-medium max-w-[120px] truncate">{current.name}</span>
        <ChevronDown className={cn("w-3.5 h-3.5 text-zinc-500 transition-transform flex-shrink-0", open && "rotate-180")} />
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-1.5 w-56 rounded-xl border border-zinc-700 bg-zinc-900 shadow-2xl z-50 py-1 overflow-hidden">
          {guilds.map(guild => (
            <button key={guild.id} onClick={() => select(guild)}
              className={cn("w-full flex items-center gap-3 px-3 py-2.5 text-sm transition-colors text-left",
                guild.id === current.id
                  ? "bg-zinc-800 text-zinc-100"
                  : "text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100")}>
              <ServerIcon guild={guild} size={6} />
              <span className="truncate font-medium">{guild.name}</span>
              {guild.id === current.id && (
                <span className="ml-auto w-1.5 h-1.5 rounded-full bg-emerald-400 flex-shrink-0" />
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function TopBar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => { logout(); navigate("/login"); };

  return (
    <div className="hidden sm:flex h-12 items-center justify-end gap-3 border-b border-zinc-800 bg-zinc-950/60 px-6 flex-shrink-0">
      <ServerSwitcher />
      <div className="w-px h-5 bg-zinc-700 mx-1" />
      {user && (
        <>
          <UserAvatar userId={user.id} username={user.username} avatarHash={user.avatar} />
          <span className="text-sm text-zinc-300 font-medium">@{user.username}</span>
        </>
      )}
      <button onClick={handleLogout} title="Logout"
        className="text-zinc-500 hover:text-red-400 transition-colors ml-1">
        <LogOut className="w-4 h-4" />
      </button>
    </div>
  );
}

export function Layout() {
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [maintenance, setMaintenance] = useState(false);

  useEffect(() => {
    // Pushed instantly by the backend (MaintenanceModeBroadcaster) instead of polling - the
    // current state is sent as the first event right after connecting, and every toggle after
    // that. Not super-admin-gated, since every manager needs to see the banner.
    const source = new EventSource(`${API_URL}/admin/maintenance/stream`, { withCredentials: true });
    source.addEventListener("maintenance", event => {
      try {
        const data = JSON.parse((event as MessageEvent).data);
        setMaintenance(!!data.enabled);
      } catch {
        // ignore malformed event
      }
    });
    return () => source.close();
  }, []);

  return (
    <div className="flex min-h-screen w-full flex-col bg-zinc-950">
      {maintenance && (
        <div className="w-full flex items-center justify-center gap-2.5 px-4 py-2.5 bg-amber-500/90 text-zinc-900 text-sm font-semibold z-50 flex-shrink-0">
          <TriangleAlert className="w-4 h-4 flex-shrink-0" />
          Bot is under maintenance — all commands are currently disabled
          <TriangleAlert className="w-4 h-4 flex-shrink-0" />
        </div>
      )}
      <div className="flex flex-1 sm:flex-row flex-col">
      {/* Mobile Header */}
      <header className="sticky top-0 z-20 flex h-16 items-center border-b border-zinc-800 bg-zinc-950/95 backdrop-blur-sm px-4 sm:hidden">
        <button
          onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
          className="inline-flex items-center justify-center rounded-md p-2 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100 focus:outline-none transition-colors"
        >
          {isMobileMenuOpen ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
        </button>
        <div className="ml-4 flex items-center gap-2 font-semibold text-zinc-100">
          <LayoutDashboard className="h-5 w-5 text-indigo-400" />
          <span>Discord Auth</span>
        </div>
      </header>

      {/* Mobile Menu Overlay */}
      {isMobileMenuOpen && (
        <div className="fixed inset-0 z-30 sm:hidden">
          <div className="fixed inset-0 bg-zinc-950/80 backdrop-blur-sm" onClick={() => setIsMobileMenuOpen(false)} />
          <div className="fixed inset-y-0 left-0 w-64 bg-zinc-950 border-r border-zinc-800 shadow-2xl">
            <div className="flex h-16 items-center border-b border-zinc-800 px-6">
              <Link to="/" className="flex items-center gap-2 font-semibold text-zinc-100" onClick={() => setIsMobileMenuOpen(false)}>
                <LayoutDashboard className="h-6 w-6 text-indigo-400" />
                <span>Discord Auth</span>
              </Link>
            </div>
            <Sidebar isMobile onNavItemClick={() => setIsMobileMenuOpen(false)} />
          </div>
        </div>
      )}

      <Sidebar maintenanceBannerShown={maintenance} />
      <div className="flex flex-col flex-1">
        <TopBar />
        <main className="flex-1">
          <Outlet />
        </main>
      </div>
      </div>
    </div>
  );
}
