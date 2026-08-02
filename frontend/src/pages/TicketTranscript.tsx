import { useEffect, useState, type ElementType } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import {
  Loader2, AlertCircle, FileText, Paperclip, ArrowLeft,
  Lock, Unlock, SlidersHorizontal, XCircle, Repeat, MailCheck, MailX, ShieldAlert,
} from "lucide-react";
import { adminApi, apiErrorMessage, type TicketTranscript as TicketTranscriptData } from "@/lib/api";
import { cn } from "@/lib/utils";

function cleanMentions(content: string): string {
  return content.replace(/<@!?(\d+)>/g, "@$1").trim();
}

const EVENT_META: Record<string, { icon: ElementType; color: string; label: (content: string) => string }> = {
  ticket_closed:    { icon: Lock,             color: "text-red-400",     label: cleanMentions },
  ticket_reopened:  { icon: Unlock,           color: "text-emerald-400", label: cleanMentions },
  ticket_controls:  { icon: SlidersHorizontal, color: "text-zinc-500",   label: () => "Ticket controls posted" },
  close_cancelled:  { icon: XCircle,          color: "text-zinc-500",    label: () => "Close cancelled" },
  repeat_trigger:   { icon: Repeat,           color: "text-amber-400",   label: cleanMentions },
  dm_sent:          { icon: MailCheck,        color: "text-sky-400",     label: () => "DM sent successfully" },
  dm_failed:        { icon: MailX,            color: "text-orange-400", label: () => "DM failed to send" },
  bot_notice:       { icon: ShieldAlert,       color: "text-red-400",    label: cleanMentions },
};

export function TicketTranscript() {
  const { channelId } = useParams<{ channelId: string }>();
  const [searchParams] = useSearchParams();
  const guildId = searchParams.get("guildId");
  const [data, setData] = useState<TicketTranscriptData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!channelId || !guildId) {
      setError("Missing channel or server id in the link.");
      setLoading(false);
      return;
    }
    let cancelled = false;
    adminApi.getTicketTranscript(channelId, guildId)
      .then(res => { if (!cancelled) setData(res); })
      .catch(err => {
        if (!cancelled) setError(apiErrorMessage(err, "Failed to load transcript."));
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [channelId, guildId]);

  return (
    <div className="flex flex-col gap-6 md:pl-64">
      <div className="px-4 sm:px-6 pt-5">
        <Link to="/transcripts"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-zinc-500 hover:text-zinc-200 transition-colors mb-2">
          <ArrowLeft className="h-3.5 w-3.5" /> Back to Transcripts
        </Link>
        <h1 className="flex items-center gap-2 text-2xl font-bold text-zinc-100 tracking-tight">
          <FileText className="h-6 w-6 text-indigo-400" /> Ticket Transcript
        </h1>
        {data && (
          <p className="text-sm text-zinc-500 mt-0.5">
            Status: <span className={data.status === "open" ? "text-emerald-400" : "text-zinc-400"}>{data.status}</span>
            {data.closedBy && (
              <>
                {" "}· Closed by {data.closedByUsername ?? data.closedBy}{" "}
                <span className="font-mono">(@{data.closedBy})</span>
              </>
            )}
            {data.closedAt && <> on {new Date(data.closedAt).toLocaleString()}</>}
          </p>
        )}
      </div>

      <div className="px-4 sm:px-6 pb-8 max-w-3xl w-full">
        {loading && (
          <div className="flex items-center gap-2 text-zinc-500 text-sm">
            <Loader2 className="w-4 h-4 animate-spin" /> Loading…
          </div>
        )}
        {error && (
          <div className="flex items-center gap-2 p-3 bg-red-500/10 border border-red-500/20 text-red-400 text-sm rounded-lg">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />{error}
          </div>
        )}
        {!loading && !error && data && (
          <div className="space-y-3">
            {data.messages.length === 0 ? (
              <p className="text-sm text-zinc-600">No messages recorded.</p>
            ) : (
              data.messages.map((msg, i) => {
                const meta = msg.eventKind ? EVENT_META[msg.eventKind] : undefined;
                if (meta) {
                  const Icon = meta.icon;
                  return (
                    <div key={i} className="flex items-center gap-2 px-2 py-1 text-xs">
                      <Icon className={cn("h-3.5 w-3.5 flex-shrink-0", meta.color)} />
                      <span className={cn("font-medium", meta.color)}>{meta.label(msg.content)}</span>
                      <span className="text-zinc-600">{new Date(msg.timestamp).toLocaleString()}</span>
                    </div>
                  );
                }
                return (
                  <div key={i} className="flex items-start gap-3 rounded-lg border border-zinc-800 bg-zinc-900 px-4 py-3">
                    {msg.authorAvatarUrl ? (
                      <img src={msg.authorAvatarUrl} alt="" className="h-8 w-8 flex-shrink-0 rounded-full" />
                    ) : (
                      <div className="h-8 w-8 flex-shrink-0 rounded-full bg-zinc-700" />
                    )}
                    <div className="min-w-0 flex-1">
                      <div className="flex items-baseline gap-2 text-xs">
                        <span className="font-semibold text-zinc-200">
                          {msg.authorTag} <span className="font-mono font-normal text-zinc-600">(@{msg.authorId})</span>
                        </span>
                        <span className="text-zinc-600">{new Date(msg.timestamp).toLocaleString()}</span>
                      </div>
                      {msg.content && (
                        <p className="mt-1 whitespace-pre-wrap break-words text-sm text-zinc-300">{msg.content}</p>
                      )}
                      {msg.attachments.length > 0 && (
                        <div className="mt-2 space-y-1">
                          {msg.attachments.map((att, j) => (
                            <a key={j} href={att.url} target="_blank" rel="noreferrer"
                              className="flex items-center gap-1.5 text-xs text-indigo-400 hover:text-indigo-300 hover:underline">
                              <Paperclip className="h-3 w-3 flex-shrink-0" /> {att.name}
                            </a>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}
      </div>
    </div>
  );
}
