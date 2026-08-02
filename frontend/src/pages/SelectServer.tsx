import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { adminApi, apiErrorMessage, type DiscordGuild } from "@/lib/api";
import { Loader2, LogOut, Server } from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";
import { cn } from "@/lib/utils";

const GUILD_STORAGE_KEY = "selected_guild_id";

export function SelectServer() {
  const [guilds, setGuilds] = useState<DiscordGuild[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const { logout } = useAuth();

  useEffect(() => {
    adminApi.getDiscordGuilds()
      .then(list => {
        setGuilds(list);
        const savedGuildId = localStorage.getItem(GUILD_STORAGE_KEY);
        const savedGuild = list.find(guild => guild.id === savedGuildId);
        if (savedGuild) {
          navigate("/", { replace: true });
        } else if (list.length === 1) {
          localStorage.setItem(GUILD_STORAGE_KEY, list[0].id);
          navigate("/", { replace: true });
        }
      })
      .catch(e => setError(apiErrorMessage(e, e?.message ?? "Failed to load servers")))
      .finally(() => setLoading(false));
  }, [navigate]);

  const handleSelect = (guild: DiscordGuild) => {
    localStorage.setItem(GUILD_STORAGE_KEY, guild.id);
    navigate("/", { replace: true });
  };

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  return (
    <div className="min-h-screen bg-zinc-950 flex flex-col">
      {/* Top bar */}
      <header className="flex items-center justify-between px-6 py-4 border-b border-zinc-800">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 bg-indigo-600 rounded-md flex items-center justify-center">
            <Server className="w-4 h-4 text-white" />
          </div>
          <span className="font-semibold text-zinc-100 text-sm">Discord Auth</span>
        </div>
        <button
          onClick={handleLogout}
          className="flex items-center gap-1.5 text-xs text-zinc-500 hover:text-red-400 transition-colors"
        >
          <LogOut className="w-3.5 h-3.5" />
          Logout
        </button>
      </header>

      {/* Content */}
      <main className="flex-1 flex flex-col items-center px-6 py-12">
        <div className="w-full max-w-3xl space-y-8">
          <div>
            <h1 className="text-2xl font-bold text-zinc-100">Servers</h1>
            <p className="text-sm text-zinc-500 mt-1">
              {loading ? "Loading your servers…" : `Servers you manage (${guilds.length})`}
            </p>
          </div>

          {loading && (
            <div className="flex items-center gap-3 text-zinc-500">
              <Loader2 className="w-5 h-5 animate-spin" />
              <span className="text-sm">Loading servers…</span>
            </div>
          )}

          {error && (
            <div className="bg-red-500/10 border border-red-500/20 text-red-400 text-sm rounded-lg px-4 py-3">
              {error}
            </div>
          )}

          {!loading && !error && guilds.length === 0 && (
            <div className="text-zinc-500 text-sm">
              No eligible servers found. You need a configured Manager Role or Super Admin access.
            </div>
          )}

          {!loading && guilds.length > 1 && (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
              {guilds.map(guild => (
                <GuildCard key={guild.id} guild={guild} onSelect={handleSelect} />
              ))}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

function GuildCard({ guild, onSelect }: { guild: DiscordGuild; onSelect: (g: DiscordGuild) => void }) {
  const [imgError, setImgError] = useState(false);

  const initials = guild.name
    .split(" ")
    .map(w => w[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

  return (
    <button
      onClick={() => onSelect(guild)}
      className={cn(
        "group flex flex-col items-center gap-3 p-4 rounded-xl border transition-all duration-150",
        "bg-zinc-900 border-zinc-800",
        "hover:bg-zinc-800 hover:border-indigo-500/50 hover:shadow-lg hover:shadow-indigo-500/5",
        "focus:outline-none focus:ring-2 focus:ring-indigo-500/40"
      )}
    >
      {/* Icon */}
      <div className="w-16 h-16 rounded-2xl overflow-hidden flex-shrink-0 transition-transform duration-150 group-hover:scale-105">
        {guild.icon && !imgError ? (
          <img
            src={guild.icon}
            alt={guild.name}
            className="w-full h-full object-cover"
            onError={() => setImgError(true)}
          />
        ) : (
          <div className="w-full h-full bg-indigo-700 flex items-center justify-center text-white font-bold text-lg">
            {initials}
          </div>
        )}
      </div>

      {/* Name */}
      <span className="text-xs font-medium text-zinc-300 text-center leading-tight line-clamp-2 group-hover:text-zinc-100 transition-colors">
        {guild.name}
      </span>
    </button>
  );
}
