import { ArrowLeft, LayoutDashboard, SearchX } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";

export function NotFound() {
  const navigate = useNavigate();

  return (
    <div className="relative flex min-h-[calc(100vh-5rem)] items-center justify-center overflow-hidden px-6 py-16 md:pl-64">
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute left-1/2 top-1/2 h-72 w-72 -translate-x-1/2 -translate-y-1/2 rounded-full bg-indigo-500/10 blur-3xl" />
        <div className="absolute left-[42%] top-[38%] h-28 w-28 rounded-full bg-rose-500/10 blur-3xl" />
      </div>

      <div className="relative w-full max-w-xl text-center">
        <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-2xl border border-indigo-500/20 bg-indigo-500/10 shadow-lg shadow-indigo-950/30">
          <SearchX className="h-8 w-8 text-indigo-400" />
        </div>

        <p className="select-none bg-gradient-to-b from-zinc-200 to-zinc-700 bg-clip-text text-8xl font-black leading-none tracking-tighter text-transparent sm:text-9xl">
          404
        </p>
        <h1 className="mt-5 text-2xl font-bold text-zinc-100 sm:text-3xl">Page not found</h1>
        <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-zinc-500">
          The page you are looking for does not exist, was moved, or you may not have access to it.
        </p>

        <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <Button asChild size="lg" className="w-full sm:w-auto">
            <Link to="/">
              <LayoutDashboard />
              Go to Dashboard
            </Link>
          </Button>
          <Button
            variant="outline"
            size="lg"
            className="w-full border-zinc-700 bg-zinc-900/60 sm:w-auto"
            onClick={() => navigate(-1)}
          >
            <ArrowLeft />
            Go back
          </Button>
        </div>
      </div>
    </div>
  );
}
