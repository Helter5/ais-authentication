import { LogIn, AlertCircle } from "lucide-react";
import { useEffect, useState } from "react";

export function Login() {
  const handleLogin = () => {
    const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:3001/api';
    window.location.href = `${API_URL}/auth/discord`;
  };

  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    const urlParams = new URLSearchParams(window.location.search);
    const errorParam = urlParams.get('error');

    if (errorParam === 'unauthorized_manager_required') {
      setErrorMessage("Access denied: configured Manager Role or Super Admin access required.");
    } else if (errorParam === 'access_revoked') {
      setErrorMessage("Your dashboard access has been revoked. Contact an administrator.");
    } else if (errorParam === 'bot_not_ready') {
      setErrorMessage("The bot just restarted and isn't connected yet. Please try again in a few seconds.");
    } else if (errorParam) {
      setErrorMessage(`Authentication failed: ${errorParam}`);
    }

    if (errorParam) {
      window.history.replaceState({}, document.title, window.location.origin + window.location.pathname);
    }
  }, []);

  return (
    <div className="min-h-screen flex items-center justify-center bg-zinc-950 p-4">
      <div className="w-full max-w-md bg-zinc-900 border border-zinc-800 rounded-2xl shadow-2xl p-8">
        <div className="flex flex-col items-center gap-4 mb-8">
          <div className="w-16 h-16 bg-indigo-500/10 rounded-2xl flex items-center justify-center">
            <LogIn className="w-8 h-8 text-indigo-400" />
          </div>
          <div className="text-center">
            <h1 className="text-2xl font-bold text-zinc-100 tracking-tight">Admin Gateway</h1>
            <p className="text-sm text-zinc-500 mt-1">Sign in to access the verification dashboard</p>
          </div>
        </div>

        {errorMessage && (
          <div className="mb-6 flex items-start gap-2.5 bg-red-500/10 border border-red-500/20 text-red-400 text-sm font-medium p-3 rounded-lg">
            <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
            {errorMessage}
          </div>
        )}

        <button
          onClick={handleLogin}
          className="w-full flex items-center justify-center gap-2.5 h-12 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-base transition-colors"
        >
          <svg className="w-5 h-5" viewBox="0 0 127.14 96.36" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
            <path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.7,77.7,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.25,105.25,0,0,0,126.6,80.22h0C129.24,52.84,122.09,29.11,107.7,8.07ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.31,60,73.31,53s5-12.74,11.43-12.74S96.32,46,96.21,53,91.08,65.69,84.69,65.69Z"/>
          </svg>
          Login with Discord
        </button>

        <p className="text-center text-xs text-zinc-600 mt-5">
          Configured managers and Super Admins only
        </p>
      </div>
    </div>
  );
}
