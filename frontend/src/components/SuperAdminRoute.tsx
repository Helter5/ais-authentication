import { useEffect, useState } from "react";
import { Navigate, Outlet } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { adminApi } from "@/lib/api";

export function SuperAdminRoute() {
  const [allowed, setAllowed] = useState<boolean | null>(null);

  useEffect(() => {
    adminApi.getAdminAccess()
      .then(result => setAllowed(result.allowed))
      .catch(() => setAllowed(false));
  }, []);

  if (allowed === null) {
    return (
      <div className="flex min-h-screen items-center justify-center md:pl-64">
        <Loader2 className="h-6 w-6 animate-spin text-indigo-400" />
      </div>
    );
  }

  return allowed ? <Outlet /> : <Navigate to="/" replace />;
}
