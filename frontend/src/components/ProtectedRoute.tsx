import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '@/contexts/AuthContext';

export function ProtectedRoute() {
  const { isAuthenticated, authLoading } = useAuth();

  if (authLoading) {
    return <div className="min-h-screen bg-zinc-950" />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}
