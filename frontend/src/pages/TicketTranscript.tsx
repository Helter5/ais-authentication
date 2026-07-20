import { useEffect, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { Loader2, AlertCircle, FileText, Paperclip } from "lucide-react";
import { adminApi, type TicketTranscript as TicketTranscriptData } from "@/lib/api";

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
        if (!cancelled) setError(err?.response?.data?.error ?? "Failed to load transcript.");
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [channelId, guildId]);

  return (
    <div className="flex flex-col gap-6 md:pl-64">
      <div className="px-4 sm:px-6 pt-5">
        <h1 className="flex items-center gap-2 text-2xl font-bold text-zinc-100 tracking-tight">
          <FileText className="h-6 w-6 text-indigo-400" /> Ticket Transcript
        </h1>
        {data && (
          <p className="text-sm text-zinc-500 mt-0.5">
            Status: <span className={data.status === "open" ? "text-emerald-400" : "text-zinc-400"}>{data.status}</span>
            {data.closedBy && <> · Closed by <span className="font-mono">{data.closedBy}</span></>}
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
              data.messages.map((msg, i) => (
                <div key={i} className="rounded-lg border border-zinc-800 bg-zinc-900 px-4 py-3">
                  <div className="flex items-baseline gap-2 text-xs">
                    <span className="font-semibold text-zinc-200">{msg.authorTag}</span>
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
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
}
