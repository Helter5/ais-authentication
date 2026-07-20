import { useCallback, useEffect, useState, useRef } from "react";
import { adminApi } from "@/lib/api";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Terminal, RefreshCw, Server, Bot } from "lucide-react";
import { cn } from "@/lib/utils";

export function DockerLogs() {
  const [logs, setLogs] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [service, setService] = useState<'bot' | 'vpn'>('bot');

  const logEndRef = useRef<HTMLDivElement>(null);

  const fetchLogs = useCallback((targetService: 'bot' | 'vpn') => {
    setLoading(true);
    setError(null);
    adminApi.getLogs(targetService)
      .then(data => setLogs(data.logs))
      .catch(err => {
        console.error(err);
        if (err.response?.status === 403) {
          setError("Forbidden: You do not have permission to view VPN logs.");
          setLogs("");
        } else {
          setError("Failed to fetch logs. Please try again.");
        }
      })
      .finally(() => setLoading(false));
  }, []);


  useEffect(() => {
    fetchLogs(service);
    const interval = setInterval(() => fetchLogs(service), 10000); // Auto refresh every 10s
    return () => clearInterval(interval);
  }, [fetchLogs, service]);

  useEffect(() => {
    if (logEndRef.current) {
      logEndRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }, [logs]);

  const handleServiceChange = (newService: 'bot' | 'vpn') => {
    setService(newService);
    setLogs(""); // Clear logs while switching
  };

  return (
    <div className="flex flex-col gap-6 md:pl-64">
      <div className="flex items-center gap-4 px-3 sm:px-6 pt-4 sm:pt-6">
        <h1 className="text-xl sm:text-3xl font-bold tracking-tight text-foreground">Docker Logs</h1>
      </div>
      
      <div className="px-3 sm:px-6 pb-6">

        <Card className="glass-card shadow-sm border-muted bg-slate-950">
          <CardHeader className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-3 border-b border-white/5">
            <div>
              <CardTitle className="flex items-center gap-2 text-primary text-base sm:text-xl">
                <Terminal className="w-4 h-4 sm:w-5 sm:h-5"/>
                {service === 'bot' ? 'Bot' : 'VPN'} Output
              </CardTitle>
              <CardDescription className="text-slate-400 text-xs sm:text-sm">
                Live output from the container.
              </CardDescription>
            </div>

            
            <div className="flex items-center gap-2 bg-white/5 p-1 rounded-lg">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => handleServiceChange('bot')}
                className={cn(
                  "h-8 px-3 text-xs",
                  service === 'bot' ? "bg-primary text-primary-foreground hover:bg-primary/90" : "text-slate-400 hover:text-white"
                )}
              >
                <Bot className="w-3.5 h-3.5 mr-1.5" />
                BOT
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => handleServiceChange('vpn')}
                className={cn(
                  "h-8 px-3 text-xs",
                  service === 'vpn' ? "bg-primary text-primary-foreground hover:bg-primary/90" : "text-slate-400 hover:text-white"
                )}
              >
                <Server className="w-3.5 h-3.5 mr-1.5" />
                VPN
              </Button>
              <div className="w-[1px] h-4 bg-white/10 mx-1" />
              <Button 
                variant="ghost" 
                size="icon" 
                onClick={() => fetchLogs(service)}
                disabled={loading}
                className="h-8 w-8 text-slate-400 hover:text-white"
              >
                <RefreshCw className={cn("w-3.5 h-3.5", loading && "animate-spin")} />
              </Button>
            </div>
          </CardHeader>
          <CardContent className="p-0">
            <div className="h-[400px] sm:h-[600px] overflow-auto scrollbar-thin p-2 sm:p-4 font-mono text-[10px] sm:text-sm bg-black/40 text-slate-300">

              {logs ? (
                <div className="whitespace-pre-wrap leading-relaxed">
                  {logs.split('\n').map((line, i) => (
                    <div key={i} className="mb-1 flex">
                      <span className="text-slate-600 mr-4 select-none w-8 text-right shrink-0">{(i+1).toString().padStart(3, '0')}</span>
                      <span>{line}</span>
                    </div>
                  ))}
                  <div ref={logEndRef} />
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center h-full text-slate-500 italic gap-3">
                  {loading ? (
                    "Streaming logs..."
                  ) : error ? (
                    <>
                      <div className="text-red-400 bg-red-500/10 border border-red-500/20 px-4 py-3 rounded-md not-italic font-sans text-center max-w-md">
                        {error}
                      </div>
                      {service === 'vpn' && <p className="text-xs">VPN logs are available to Super Admins.</p>}
                    </>
                  ) : (
                    "No logs available or container unreachable."
                  )}
                </div>
              )}

            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
