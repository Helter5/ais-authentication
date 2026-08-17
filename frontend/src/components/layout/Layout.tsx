import { Outlet, useNavigate } from "react-router-dom";
import { Sidebar } from "./Sidebar";
import { useEffect, useRef, useState } from "react";
import { Menu, X, LayoutDashboard, LogOut, ChevronDown, TriangleAlert, ShieldCheck, SlidersHorizontal, LayoutGrid } from "lucide-react";
import { Link } from "react-router-dom";
import { useAuth } from "@/contexts/AuthContext";
import { adminApi } from "@/lib/api";
import { cn } from "@/lib/utils";
import { setSelectedGuildId, useSelectedGuildId } from "@/components/modules/shared";

const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080/api').replace(/\/$/, '');
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

/**
 * Live via SSE (VerificationStatusBroadcaster on the backend) instead of polling - same pattern
 * as the maintenance-mode banner, except this is per-guild so the subscription is re-opened
 * whenever the selected guild changes, tagged with that guildId server-side.
 */
function VerificationStatusDot({ maintenance }: { maintenance: boolean }) {
  const guildId = useSelectedGuildId();
  const [enabled, setEnabled] = useState<boolean | null>(null);

  useEffect(() => {
    setEnabled(null);
    if (!guildId) return;
    const source = new EventSource(`${API_URL}/settings/verification-stream?guildId=${guildId}`, { withCredentials: true });
    source.addEventListener("verification", event => {
      try {
        const data = JSON.parse((event as MessageEvent).data);
        setEnabled(!!data.enabled);
      } catch {
        // ignore malformed event
      }
    });
    return () => source.close();
  }, [guildId]);

  if (enabled === null) return null;

  // verification_enabled is a per-guild setting, independent of the global maintenance-mode flag
  // that blocks every command (including /verify) at a different layer - without this override the
  // badge would stay green even though /verify is actually unusable while maintenance is on.
  const blocked = maintenance && enabled;
  const label = blocked ? "Blocked by maintenance mode" : enabled ? "Enabled" : "Disabled";
  const color = blocked ? "text-amber-400" : enabled ? "text-emerald-400" : "text-zinc-600";
  const dotColor = blocked
    ? "bg-amber-400 shadow-[0_0_6px_rgba(251,191,36,0.6)]"
    : enabled ? "bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.6)]" : "bg-red-500";

  return (
    <div
      title={`Verification: ${label}`}
      className="flex items-center gap-1.5 px-2 py-1 rounded-md bg-zinc-800/40"
    >
      <ShieldCheck className={cn("w-3.5 h-3.5", color)} />
      <span className={cn("w-1.5 h-1.5 rounded-full flex-shrink-0", dotColor)} />
    </div>
  );
}

/**
 * Whether the currently selected guild is on the allowlist - if it isn't, every command and
 * module event is silently dropped for it (CommandInteractionListener / GuildAllowlistEventManager),
 * independent of and more severe than global maintenance mode, so Commands/Modules must check this
 * per-guild too instead of only reflecting the global maintenance flag.
 */
function useGuildAllowed(): boolean | null {
  const guildId = useSelectedGuildId();
  const [allowed, setAllowed] = useState<boolean | null>(null);

  useEffect(() => {
    setAllowed(null);
    if (!guildId) return;
    let cancelled = false;
    adminApi.getGuildAllowed(guildId)
      .then(r => { if (!cancelled) setAllowed(r.allowed); })
      .catch(() => { if (!cancelled) setAllowed(null); });
    return () => { cancelled = true; };
  }, [guildId]);

  return allowed;
}

function botWideStatus(maintenance: boolean, guildAllowed: boolean | null) {
  if (guildAllowed === false) {
    return {
      label: "Blocked - this server isn't on the allowlist",
      color: "text-red-500",
      dotColor: "bg-red-500 shadow-[0_0_6px_rgba(239,68,68,0.6)]",
    };
  }
  if (maintenance) {
    return {
      label: "Blocked by maintenance mode",
      color: "text-amber-400",
      dotColor: "bg-amber-400 shadow-[0_0_6px_rgba(251,191,36,0.6)]",
    };
  }
  return {
    label: "Enabled",
    color: "text-emerald-400",
    dotColor: "bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.6)]",
  };
}

/** Maintenance mode blocks every slash command bot-wide; not being on the allowlist blocks them per-guild. */
function CommandsStatusDot({ maintenance }: { maintenance: boolean }) {
  const guildAllowed = useGuildAllowed();
  const { label, color, dotColor } = botWideStatus(maintenance, guildAllowed);

  return (
    <div title={`Commands: ${label}`} className="flex items-center gap-1.5 px-2 py-1 rounded-md bg-zinc-800/40">
      <SlidersHorizontal className={cn("w-3.5 h-3.5", color)} />
      <span className={cn("w-1.5 h-1.5 rounded-full flex-shrink-0", dotColor)} />
    </div>
  );
}

/** Maintenance mode pauses every module listener bot-wide; not being on the allowlist blocks them per-guild. */
function ModulesStatusDot({ maintenance }: { maintenance: boolean }) {
  const guildAllowed = useGuildAllowed();
  const { label, color, dotColor } = botWideStatus(maintenance, guildAllowed);

  return (
    <div title={`Modules: ${label}`} className="flex items-center gap-1.5 px-2 py-1 rounded-md bg-zinc-800/40">
      <LayoutGrid className={cn("w-3.5 h-3.5", color)} />
      <span className={cn("w-1.5 h-1.5 rounded-full flex-shrink-0", dotColor)} />
    </div>
  );
}

type DiscordIndicator = "none" | "minor" | "major" | "critical";
const DISCORD_STATUS_POLL_MS = 60_000;

function DiscordGlyph({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 127.14 96.36" fill="currentColor" className={className} aria-hidden="true">
      <path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.7,77.7,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.25,105.25,0,0,0,126.6,80.22h0C129.24,52.84,122.09,29.11,107.7,8.07ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z" />
    </svg>
  );
}

/**
 * Solid dot with a larger pulsing ring expanding behind it, signaling this badge polls a live
 * source - the ring is sized independently of the dot (rather than just scaling the dot itself)
 * so the pulse stays noticeable at a glance instead of disappearing into a few pixels.
 */
function LiveDot({ className }: { className: string }) {
  return (
    <span className="relative flex items-center justify-center w-3.5 h-3.5 flex-shrink-0">
      <span className={cn("absolute inline-flex w-3.5 h-3.5 rounded-full opacity-60 animate-ping [animation-duration:1.5s]", className)} />
      <span className={cn("relative inline-flex w-2 h-2 rounded-full", className)} />
    </span>
  );
}

/**
 * discordstatus.com is a statuspage.io instance with an open CORS policy on its public summary
 * API, so this polls it directly from the browser - no backend proxy needed for a read-only,
 * unauthenticated, third-party status check.
 */
function DiscordStatusDot() {
  const [status, setStatus] = useState<{ indicator: DiscordIndicator; description: string } | null>(null);

  useEffect(() => {
    let cancelled = false;
    const fetchStatus = () => {
      fetch("https://discordstatus.com/api/v2/status.json")
        .then(res => res.json())
        .then(data => { if (!cancelled) setStatus(data.status); })
        .catch(() => { if (!cancelled) setStatus(null); });
    };
    fetchStatus();
    const interval = setInterval(fetchStatus, DISCORD_STATUS_POLL_MS);
    return () => { cancelled = true; clearInterval(interval); };
  }, []);

  if (!status) return null;

  const color = status.indicator === "none" ? "text-indigo-400" : status.indicator === "minor" ? "text-amber-400" : "text-red-500";
  const dotColor = status.indicator === "none" ? "bg-emerald-400" : status.indicator === "minor" ? "bg-amber-400" : "bg-red-500";

  return (
    <div title={`Discord API status (live): ${status.description}`} className="flex items-center gap-1.5 px-2 py-1 rounded-md bg-zinc-800/40">
      <DiscordGlyph className={cn("w-3.5 h-3.5", color)} />
      <LiveDot className={dotColor} />
    </div>
  );
}

function TopBar({ maintenance }: { maintenance: boolean }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => { logout(); navigate("/login"); };

  return (
    <div className="hidden sm:flex h-12 items-center justify-between gap-3 border-b border-zinc-800 bg-zinc-950/60 pl-6 pr-6 sm:pl-[280px] flex-shrink-0">
      <div className="flex items-center gap-2">
        <DiscordStatusDot />
        <VerificationStatusDot maintenance={maintenance} />
        <CommandsStatusDot maintenance={maintenance} />
        <ModulesStatusDot maintenance={maintenance} />
      </div>
      <div className="flex items-center gap-3">
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
          Bot is under maintenance
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
        <TopBar maintenance={maintenance} />
        <main className="flex-1">
          <Outlet />
        </main>
      </div>
      </div>
    </div>
  );
}
