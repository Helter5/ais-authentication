import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';

const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:3001/api').replace(/\/$/, '');

type AuthUser = { id: string; username: string; avatar?: string | null; guildIds?: string[] };

interface AuthContextType {
  isAuthenticated: boolean;
  authLoading: boolean;
  user: AuthUser | null;
  login: (user: AuthUser) => void;
  logout: () => void;
}


const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [authLoading, setAuthLoading] = useState(true);

  const login = (authUser: AuthUser) => {
    setUser(authUser);
  };

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');

    if (code) {
      // Remove code from URL immediately
      window.history.replaceState({}, document.title, window.location.origin + window.location.pathname);
      // Exchange one-time code for a JWT; the server sets it as an httpOnly cookie (Fix 1)
      fetch(`${API_URL}/auth/exchange`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code }),
      })
        .then(r => r.json())
        .then(data => { if (data.user) login(data.user); })
        .catch(console.error)
        .finally(() => setAuthLoading(false));
    } else {
      fetch(`${API_URL}/auth/session`, { credentials: 'include' })
        .then(async response => {
          if (!response.ok) throw new Error('Session invalid');
          const data = await response.json();
          setUser(data.user);
        })
        .catch(() => {
          localStorage.removeItem('selected_guild_id');
          setUser(null);
        })
        .finally(() => setAuthLoading(false));
    }
  }, []);

  const logout = () => {
    setUser(null);
    // Revoke server-side session and clear the auth cookie (Fix 4)
    fetch(`${API_URL}/auth/logout`, { method: 'POST', credentials: 'include' }).catch(() => {});
  };

  return (
    <AuthContext.Provider value={{ isAuthenticated: !!user, authLoading, user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}


export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
