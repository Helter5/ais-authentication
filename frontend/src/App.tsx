import { lazy, Suspense } from "react";
import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { Layout } from "@/components/layout/Layout";
import { AuthProvider } from "@/contexts/AuthContext";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { SuperAdminRoute } from "@/components/SuperAdminRoute";
import { ToastProvider } from "@/components/ui/toast";

const Codes = lazy(() => import("@/pages/Codes").then(module => ({ default: module.Codes })));
const Users = lazy(() => import("@/pages/Users").then(module => ({ default: module.Users })));
const DockerLogs = lazy(() => import("@/pages/DockerLogs").then(module => ({ default: module.DockerLogs })));
const Logs = lazy(() => import("@/pages/Logs").then(module => ({ default: module.Logs })));
const Admin = lazy(() => import("@/pages/Admin").then(module => ({ default: module.Admin })));
const Commands = lazy(() => import("@/pages/Commands").then(module => ({ default: module.Commands })));
const Modules = lazy(() => import("@/pages/Modules").then(module => ({ default: module.Modules })));
const HackedAccountTrapModule = lazy(() => import("@/pages/HackedAccountTrap").then(module => ({ default: module.HackedAccountTrapModule })));
const ReactionRolesModule = lazy(() => import("@/pages/ReactionRoles").then(module => ({ default: module.ReactionRolesModule })));
const SwitchSemesterModule = lazy(() => import("@/pages/SwitchSemester").then(module => ({ default: module.SwitchSemesterModule })));
const AutoDeleteModule = lazy(() => import("@/pages/AutoDelete").then(module => ({ default: module.AutoDeleteModule })));
const TicketTranscript = lazy(() => import("@/pages/TicketTranscript").then(module => ({ default: module.TicketTranscript })));
const Settings = lazy(() => import("@/pages/Settings").then(module => ({ default: module.Settings })));
const Wipe = lazy(() => import("@/pages/Wipe").then(module => ({ default: module.Wipe })));
const SelectServer = lazy(() => import("@/pages/SelectServer").then(module => ({ default: module.SelectServer })));
const Login = lazy(() => import("@/pages/Login").then(module => ({ default: module.Login })));
const Dashboard = lazy(() => import("@/pages/Dashboard").then(module => ({ default: module.Dashboard })));
const NotFound = lazy(() => import("@/pages/NotFound").then(module => ({ default: module.NotFound })));

function PageFallback() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-zinc-950">
      <Loader2 className="h-6 w-6 animate-spin text-indigo-400" />
    </div>
  );
}

function App() {
  return (
    <ToastProvider>
    <AuthProvider>
      <Router>
        <Suspense fallback={<PageFallback />}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/select-server" element={<SelectServer />} />
            <Route element={<Layout />}>
                <Route path="/" element={<Dashboard />} />
                <Route path="/codes" element={<Codes />} />
                <Route path="/users" element={<Users />} />
                <Route path="/semester" element={<SwitchSemesterModule />} />
                <Route path="/semester/switch" element={<SwitchSemesterModule />} />
                <Route path="/semester/setup" element={<SwitchSemesterModule />} />
                <Route path="/access-logs" element={<Logs />} />
                <Route path="/tickets/:channelId" element={<TicketTranscript />} />
                <Route element={<SuperAdminRoute />}>
                  <Route path="/logs" element={<DockerLogs />} />
                  <Route path="/modules" element={<Modules />} />
                  <Route path="/modules/hacked-account-trap" element={<HackedAccountTrapModule />} />
                  <Route path="/modules/reaction-roles" element={<ReactionRolesModule />} />
                  <Route path="/modules/autodelete" element={<AutoDeleteModule />} />
                  <Route path="/commands/switchsemester" element={<SwitchSemesterModule />} />
                  {/* legacy redirect handled by duplicate route above */}
                  <Route path="/commands" element={<Commands />} />
                  <Route path="/wipe" element={<Wipe />} />
                  <Route path="/settings" element={<Settings />} />
                  <Route path="/admin" element={<Admin />} />
                </Route>
                <Route path="*" element={<NotFound />} />
            </Route>
          </Route>
        </Routes>
        </Suspense>
      </Router>
    </AuthProvider>
    </ToastProvider>
  );
}

export default App;
