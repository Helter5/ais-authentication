import axios from 'axios';
import { refreshSession } from './authRefresh';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const api = axios.create({ baseURL: API_URL, withCredentials: true });

api.interceptors.response.use(
  res => res,
  async err => {
    const originalRequest = err.config;
    const isAuthEndpoint = typeof originalRequest?.url === 'string' && originalRequest.url.includes('/auth/');

    if (err.response?.status === 401 && !isAuthEndpoint && originalRequest && !originalRequest._retry) {
      originalRequest._retry = true;
      if (await refreshSession()) {
        return api(originalRequest);
      }
    }

    if (err.response?.status === 401) {
      window.location.href = '/login';
    }
    if (err.response?.status === 403 && err.response?.data?.detail?.includes('Manager access required')) {
      localStorage.removeItem('selected_guild_id');
      window.location.href = '/login?error=access_revoked';
    }
    return Promise.reject(err);
  }
);

/**
 * Backend validation errors are Spring's RFC 7807 ProblemDetail — the message lives under
 * `detail`, not `error`. Every catch block that surfaces an API error message should go through
 * this instead of reading `err.response?.data?.error` directly, which is always undefined and
 * silently falls back to a generic message no matter what the backend actually rejected.
 */
export function apiErrorMessage(err: unknown, fallback: string): string {
  const data = (err as { response?: { data?: { error?: string; detail?: string } } })?.response?.data;
  return data?.error ?? data?.detail ?? fallback;
}

export interface VerificationCode {
  id: number;
  discord_id: string;
  guild_id: string;
  email: string;
  ais_id: string;
  created_at: string;
  expires_at: string;
}

export interface VerifiedUser {
  id: number;
  ais_id: string;
  discord_id: string;
  guild_id: string;
  email: string;
  verified_at: string;
}

export interface DiscordGuild {
  id: string;
  name: string;
  icon: string | null;
}

export type RevertStatus = 'NONE' | 'PARTIAL' | 'FULL';

export interface SwitchHistoryEntry {
  id: number;
  migrationId: string;
  operationType: 'setup' | 'plan';
  label: string;
  actorId: string | null;
  actorName: string | null;
  createdAt: string;
  canRevertPosition: boolean;
  positionReverted: boolean;
  status: RevertStatus;
  rolledBackByActorName: string | null;
}

export interface MigrationGroup {
  groupKey: string;
  roleFromId: string;
  roleToId: string | null;
  keptFromRole: boolean;
  totalMembers: number;
  remainingMembers: number;
  rolledBack: boolean;
  discordIds: string[];
}

export interface VisibilityRow {
  id: number;
  categoryId: string;
  categoryName: string | null;
  direction: 'hide' | 'show';
  everyoneViewChannel: boolean;
  rolledBack: boolean;
  isChannel: boolean;
}

export interface StepDetail {
  stepIndex: number;
  stepLabel: string | null;
  roleGroups: MigrationGroup[];
  visibilityRows: VisibilityRow[];
}

export interface MigrationDetail {
  steps: StepDetail[];
}

export interface AdditionalChannel {
  channelId: string;
  channelName: string;
  visible?: boolean;
  everyoneViewChannel?: boolean;
}

export type SwitchPlanStep =
  | { type: 'switch'; from: string; to: string }
  | { type: 'setup'; semester: string; visible?: boolean; clearRoles?: boolean };

export interface SwitchPlan {
  id: string;
  name: string;
  steps: SwitchPlanStep[];
  additionalChannels?: AdditionalChannel[];
}

export interface AuditLog {
  id: number;
  category: 'dashboard' | 'commands' | 'automod';
  action: string;
  guild_id: string | null;
  guild_name: string | null;
  channel_id: string | null;
  channel_name: string | null;
  user_id: string | null;
  username: string | null;
  details: Record<string, unknown> | null;
  created_at: string;
}

export interface DashboardData {
  server: {
    id: string;
    name: string;
    icon: string | null;
    memberCount: number;
    categoryCount: number;
    textChannelCount: number;
    voiceChannelCount: number;
    roleCount: number;
  };
  synchronization: {
    lastSyncAt: string | null;
    checkedCount: number | null;
    removedCount: number | null;
    removedUsers: {
      discordId: string;
      username: string | null;
      aisId: string;
      email: string;
    }[];
  };
  verificationConfig: {
    allowedFaculties: string[];
    requiredAccountStatus: string;
    verifiedRoleId: string | null;
    verifiedRoleName: string | null;
    inactiveRoleId: string | null;
    inactiveRoleName: string | null;
    verifiedCount: number;
  };
  recentActivity: {
    category: string;
    action: string;
    username: string | null;
    createdAt: string;
  }[];
}

/** Discord's own ban-dialog "delete message history" durations, in seconds. */
export type DeleteMessageHistorySeconds = 3600 | 21600 | 43200 | 86400 | 259200 | 604800;

export interface HackedAccountTrapSettings {
  enabled: boolean;
  trapChannelId: string | null;
  ignoreAdministrators: boolean;
  exemptRoleIds: string[];
  deleteMessageHistory: boolean;
  deleteMessageHistorySeconds: DeleteMessageHistorySeconds;
  dmUser: boolean;
  dmMessage: string;
}

export interface AutoMention {
  id: number;
  channel_id: string;
  role_id: string;
  enabled: boolean;
  delete_after_seconds: number | null;
}

export interface AutoDeleteConfig {
  id: number;
  guild_id: string;
  channel_id: string;
  delay_seconds: number;
  ignore_role_ids: string[];
  ignore_user_ids: string[];
  ignore_pinned: boolean;
  notify_user: boolean;
  notify_via: 'channel' | 'dm';
  notify_message: string;
  notify_delete_bot_msg: boolean;
  notify_delete_delay: number;
}

export interface DiscordEmoji {
  id: string;
  name: string;
  animated: boolean;
  url: string;
  mention: string;
}

export interface RoleMenuOption {
  role_ids: string[];
  label: string;
  emoji: string | null;
  description: string | null;
}

export interface RoleMenuConfig {
  id: number;
  guild_id: string;
  channel_id: string;
  message_id: string | null;
  /** Dashboard-only label for the sidebar list - never sent to Discord (that's what `title` is for). */
  config_name: string | null;
  title: string;
  description: string;
  ui_type: 'BUTTONS' | 'SELECT_MENU';
  message_type: 'NORMAL' | 'UNIQUE' | 'VERIFY' | 'DROP' | 'BINDING';
  require_verified: boolean;
  options: RoleMenuOption[];
  allowed_role_ids: string[];
  blocked_role_ids: string[];
  max_selectable: number | null;
}

export interface ThesisCounterConfig {
  id: number;
  guild_id: string;
  channel_id: string;
  label: 'BP' | 'DP';
  target_date: string; // ISO yyyy-MM-dd
  active: boolean;
  days_remaining: number;
  name_format: string | null;
  today_format: string | null;
}

export type LdapStatusRange = 'hour' | 'day' | 'week';

export interface LdapStatusBucket {
  bucketStart: string;
  successCount: number;
  failCount: number;
  avgLatencyMs: number | null;
}

export interface LdapStatus {
  currentlyUp: boolean;
  lastCheckedAt: string | null;
  lastLatencyMs: number | null;
  uptimePercent: number;
  buckets: LdapStatusBucket[];
}

export const adminApi = {
  getAdminAccess: async (): Promise<{ allowed: boolean }> => {
    const res = await api.get('/admin/access');
    return res.data;
  },

  getAccessLogs: async (guildId: string, limit = 100): Promise<{ id: number; discord_id: string; username: string; action: string; guild_id: string; ip: string | null; created_at: string }[]> => {
    const res = await api.get('/access-logs', { params: { guildId, limit } });
    return res.data;
  },

  getAuditLogs: async (category: 'dashboard' | 'commands' | 'automod' | 'verification', guildId: string, limit = 200): Promise<AuditLog[]> => {
    const res = await api.get('/admin/audit-logs', { params: { category, guildId, limit } });
    return res.data;
  },

  getVerifications: async (guildId: string): Promise<VerificationCode[]> => {
    const res = await api.get('/verifications', { params: { guildId } });
    return res.data;
  },

  getUsers: async (guildId: string): Promise<VerifiedUser[]> => {
    const res = await api.get('/users', { params: { guildId } });
    return res.data;
  },

  getDashboard: async (guildId: string): Promise<DashboardData> => {
    const res = await api.get('/dashboard', { params: { guildId } });
    return res.data;
  },

  getManagerRoles: async (guildId: string): Promise<{
    roles: { id: string; name: string; color: string; position: number }[];
    managerRoleIds: string[];
  }> => {
    const res = await api.get('/admin/manager-roles', { params: { guildId } });
    return res.data;
  },

  updateManagerRoles: async (guildId: string, managerRoleIds: string[]): Promise<{ success: boolean; managerRoleIds: string[] }> => {
    const res = await api.post('/admin/manager-roles', { guildId, managerRoleIds });
    return res.data;
  },

  getAdminStatus: async (): Promise<{ uptime: number; guildCount: number; verifiedCount: number; activeCodesCount: number; nodeVersion: string; memoryMB: number }> => {
    const res = await api.get('/admin/status');
    return res.data;
  },

  getAdminWarnings: async (guildId: string): Promise<{
    id: number;
    guild_id: string;
    guild_name: string | null;
    discord_id: string;
    target_name: string | null;
    moderator_id: string;
    moderator_name: string | null;
    reason: string;
    created_at: string;
  }[]> => {
    const res = await api.get('/admin/warnings', { params: { guildId } });
    return res.data;
  },

  getBotGuilds: async (): Promise<{ id: string; name: string; icon: string | null; memberCount: number; verifiedCount: number; allowed: boolean }[]> => {
    const res = await api.get('/admin/bot-guilds');
    return res.data;
  },

  getAllowedGuilds: async (): Promise<{ guildIds: string[] }> => {
    const res = await api.get('/admin/allowed-guilds');
    return res.data;
  },

  setAllowedGuilds: async (guildIds: string[]): Promise<{ success: boolean; guildIds: string[] }> => {
    const res = await api.post('/admin/allowed-guilds', { guildIds });
    return res.data;
  },

  getGuildAllowed: async (guildId: string): Promise<{ allowed: boolean }> => {
    const res = await api.get('/admin/guild-allowed', { params: { guildId } });
    return res.data;
  },

  getMaintenance: async (): Promise<{ enabled: boolean }> => {
    const res = await api.get('/admin/maintenance');
    return res.data;
  },

  setMaintenance: async (enabled: boolean): Promise<{ success: boolean }> => {
    const res = await api.post('/admin/maintenance', { enabled });
    return res.data;
  },

  getAdminSettings: async (): Promise<{ super_admin_users: string }> => {
    const res = await api.get('/admin/settings');
    return res.data;
  },

  getLdapStatus: async (range: LdapStatusRange = 'day'): Promise<LdapStatus> => {
    const res = await api.get('/admin/ldap-status', { params: { range } });
    return res.data;
  },

  // ── Server Settings ────────────────────────────────────────────────────────

  getDiscordRoles: async (guildId: string): Promise<{ id: string; name: string; color: string; position: number }[]> => {
    const res = await api.get('/discord/roles', { params: { guildId } });
    return res.data;
  },

  getDiscordTextChannels: async (guildId: string): Promise<{ id: string; name: string; position: number }[]> => {
    const res = await api.get('/discord/channels', { params: { guildId } });
    return res.data;
  },

  getDiscordEmojis: async (guildId: string): Promise<DiscordEmoji[]> => {
    const res = await api.get('/discord/emojis', { params: { guildId } });
    return res.data;
  },

  /** Resolves member IDs to their current username - e.g. for showing "who exactly" in a semester
   *  role-migration's member list instead of raw Discord IDs. Empty ids returns [] without a request. */
  getDiscordMembers: async (guildId: string, ids: string[]): Promise<{ id: string; name: string }[]> => {
    if (ids.length === 0) return [];
    const res = await api.get('/discord/members', { params: { guildId, ids: ids.join(",") } });
    return res.data;
  },

  getSettings: async (guildId: string): Promise<{
    verified_role_id: string | null;
    inactive_role_id: string | null;
    spam_trap_channel_id: string | null;
    spam_delete_interval: number;
    verification_enabled: boolean;
  }> => {
    const res = await api.get('/settings', { params: { guildId } });
    return res.data;
  },

  updateSetting: async (guildId: string, field: string, value: unknown): Promise<{ success: boolean }> => {
    const res = await api.patch('/settings', { guildId, field, value });
    return res.data;
  },

  updateSettingsBulk: async (guildId: string, fields: Record<string, unknown>): Promise<{ success: boolean }> => {
    const res = await api.patch('/settings/bulk', { guildId, fields });
    return res.data;
  },

  getLogChannels: async (guildId: string): Promise<{
    eventTypes: {
      eventType: string;
      label: string;
      description: string;
      channelId: string | null;
      channelName: string | null;
      ok: boolean;
      configured: boolean;
      error: string | null;
    }[];
  }> => {
    const res = await api.get('/log-channels', { params: { guildId } });
    return res.data;
  },

  updateLogChannels: async (guildId: string, assignments: Record<string, string | null>): Promise<{ success: boolean }> => {
    const res = await api.put('/log-channels', { guildId, assignments });
    return res.data;
  },

  getHackedAccountTrap: async (guildId: string): Promise<HackedAccountTrapSettings> => {
    const res = await api.get('/modules/hacked-account-trap', { params: { guildId } });
    return res.data;
  },

  saveHackedAccountTrap: async (guildId: string, settings: HackedAccountTrapSettings): Promise<HackedAccountTrapSettings> => {
    const res = await api.post('/modules/hacked-account-trap', { guildId, ...settings });
    return res.data;
  },

  getWarnThresholds: async (guildId: string): Promise<{ warn_limit: number; action: string }[]> => {
    const res = await api.get('/warn-thresholds', { params: { guildId } });
    return res.data;
  },

  addWarnThreshold: async (guildId: string, warn_limit: number, action: string): Promise<{ success: boolean }> => {
    const res = await api.post('/warn-thresholds', { guildId, warn_limit, action });
    return res.data;
  },

  removeWarnThreshold: async (guildId: string, limit: number): Promise<{ success: boolean }> => {
    const res = await api.delete(`/warn-thresholds/${limit}`, { params: { guildId } });
    return res.data;
  },

  getAutoMentionEnabled: async (guildId: string): Promise<{ enabled: boolean }> => {
    const res = await api.get('/auto-mentions/enabled', { params: { guildId } });
    return res.data;
  },

  setAutoMentionEnabled: async (guildId: string, enabled: boolean): Promise<{ success: boolean }> => {
    const res = await api.post('/auto-mentions/enabled', { guildId, enabled });
    return res.data;
  },

  getAutoMentions: async (guildId: string): Promise<AutoMention[]> => {
    const res = await api.get('/auto-mentions', { params: { guildId } });
    return res.data;
  },

  createAutoMention: async (guildId: string, data: Omit<AutoMention, "id">): Promise<AutoMention> => {
    const res = await api.post('/auto-mentions', { guildId, ...data });
    return res.data;
  },

  updateAutoMention: async (id: number, guildId: string, data: Omit<AutoMention, "id">): Promise<AutoMention> => {
    const res = await api.patch(`/auto-mentions/${id}`, { guildId, ...data });
    return res.data;
  },

  deleteAutoMention: async (id: number, guildId: string): Promise<{ success: boolean }> => {
    const res = await api.delete(`/auto-mentions/${id}`, { params: { guildId } });
    return res.data;
  },

  getCommandStates: async (guildId: string): Promise<Record<string, boolean>> => {
    const res = await api.get('/command-states', { params: { guildId } });
    return res.data;
  },

  setCommandEnabled: async (guildId: string, command: string, enabled: boolean): Promise<{ success: boolean }> => {
    const res = await api.patch('/command-states', { guildId, command, enabled });
    return res.data;
  },

  setCommandStatesBulk: async (guildId: string, commands: Record<string, boolean>): Promise<{ success: boolean }> => {
    const res = await api.patch('/command-states/bulk', { guildId, commands });
    return res.data;
  },

  getCommandPermissionsSummary: async (guildId: string): Promise<{ command: string; allowedRoles: string[]; ignoredRoles: string[]; allowedChannels: string[]; ignoredChannels: string[]; adminOnly: boolean }[]> => {
    const res = await api.get('/command-permissions/summary', { params: { guildId } });
    return res.data;
  },

  getCommandPermissions: async (guildId: string, command: string): Promise<{ allowedChannels: string[]; ignoredChannels: string[]; allowedRoles: string[]; ignoredRoles: string[]; adminOnly: boolean }> => {
    const res = await api.get('/command-permissions', { params: { guildId, command } });
    return res.data;
  },

  saveCommandPermissions: async (guildId: string, command: string, perms: { allowedChannels: string[]; ignoredChannels: string[]; allowedRoles: string[]; ignoredRoles: string[]; adminOnly: boolean }): Promise<{ success: boolean }> => {
    const res = await api.post('/command-permissions', { guildId, command, ...perms });
    return res.data;
  },

  deployCommands: async (guildId: string): Promise<{ success: boolean; count: number }> => {
    const res = await api.post('/deploy-commands', { guildId });
    return res.data;
  },

  getWipeAccess: async (guildId: string): Promise<{ allowed: boolean; reason?: string }> => {
    const res = await api.get('/wipe/access', { params: { guildId } });
    return res.data;
  },

  getWipeSettings: async (guildId: string): Promise<{ removeAllRoles: boolean; keepRoleIds: string[] }> => {
    const res = await api.get('/wipe/settings', { params: { guildId } });
    return res.data;
  },

  saveWipeSettings: async (guildId: string, removeAllRoles: boolean, keepRoleIds: string[]): Promise<{ removeAllRoles: boolean; keepRoleIds: string[] }> => {
    const res = await api.post('/wipe/settings', { guildId, removeAllRoles, keepRoleIds });
    return res.data;
  },

  getSemesterAccess: async (guildId: string): Promise<{ allowed: boolean; reason?: string }> => {
    const res = await api.get('/semester/access', { params: { guildId } });
    return res.data;
  },

  getWipeStatus: async (guildId: string): Promise<{
    running: boolean;
    done: boolean;
    logs: { time: string; msg: string; level: 'info' | 'warn' | 'error' | 'success' }[];
    stats: { total: number; processed: number; inactive: number; errors: number } | null;
    startedAt: string | null;
    completedAt: string | null;
  }> => {
    const res = await api.get('/wipe/status', { params: { guildId } });
    return res.data;
  },

  startWipe: async (guildId: string, removeAllRoles = false, keepRoleIds: string[] = []): Promise<{ started: boolean; total: number }> => {
    const res = await api.post('/wipe', { guildId, removeAllRoles, keepRoleIds });
    return res.data;
  },

  getCommandSettings: async (guildId: string, command: string): Promise<Record<string, unknown>> => {
    const res = await api.get('/command-settings', { params: { guildId, command } });
    return res.data;
  },

  saveCommandSettings: async (guildId: string, command: string, settings: Record<string, unknown>): Promise<{ success: boolean }> => {
    const res = await api.post('/command-settings', { guildId, command, ...settings });
    return res.data;
  },

  getSemesterConfigs: async (guildId: string): Promise<Record<string, unknown>> => {
    const res = await api.get('/semester/configs', { params: { guildId } });
    return res.data;
  },

  saveSemesterConfigs: async (guildId: string, settings: Record<string, unknown>): Promise<{ success: boolean }> => {
    const res = await api.post('/semester/configs', { guildId, ...settings });
    return res.data;
  },

  getPlanProgress: async (guildId: string): Promise<{ running: boolean; progress: number; logs: string[]; startedAt: string | null; status?: 'running' | 'success' | 'partial' | 'failed'; operation?: 'plan'; params?: { planId: string }; completedSteps?: string[] }> => {
    const res = await api.get('/switchplan/progress', { params: { guildId } });
    return res.data;
  },

  runPlan: async (guildId: string, planId: string, resume = false): Promise<{ started: boolean }> => {
    const res = await api.post('/switchplan/run', { guildId, planId, resume });
    return res.data;
  },

  getSemesterSetupProgress: async (guildId: string): Promise<{ running: boolean; progress: number; logs: string[]; startedAt: string | null; status?: 'running' | 'success' | 'partial' | 'failed'; operation?: 'setup'; params?: { semesterName: string; visible: boolean; clearRoles: boolean }; completedSteps?: string[] }> => {
    const res = await api.get('/setupsemester/progress', { params: { guildId } });
    return res.data;
  },

  runSemesterSetup: async (guildId: string, semesterName: string, visible: boolean, clearRoles: boolean, resume = false): Promise<{ started: boolean }> => {
    const res = await api.post('/setupsemester/run', { guildId, semesterName, visible, clearRoles, resume });
    return res.data;
  },

  getCurrentPlan: async (guildId: string): Promise<{ currentPlanId: string | null; currentPlanName: string | null; currentSemesterType: 'WINTER' | 'SUMMER' | null }> => {
    const res = await api.get('/semester/current', { params: { guildId } });
    return res.data;
  },

  setCurrentPlan: async (guildId: string, planId: string): Promise<{ success: boolean }> => {
    const res = await api.post('/semester/current', { guildId, planId });
    return res.data;
  },

  getNextPlan: async (guildId: string): Promise<{
    currentPlanId: string | null; currentPlanName: string | null; currentSemesterType: 'WINTER' | 'SUMMER' | null;
    nextPlanId: string | null; nextPlanName: string | null; nextSemesterType: 'WINTER' | 'SUMMER' | null;
  }> => {
    const res = await api.get('/semester/next', { params: { guildId } });
    return res.data;
  },

  getSwitchSemesterHistory: async (guildId: string): Promise<SwitchHistoryEntry[]> => {
    const res = await api.get('/switchsemester/history', { params: { guildId } });
    return res.data;
  },

  getMigrationDetail: async (guildId: string, migrationId: string): Promise<MigrationDetail> => {
    const res = await api.get(`/switchsemester/migration/${migrationId}/detail`, { params: { guildId } });
    return res.data;
  },

  getRollbackProgress: async (guildId: string): Promise<{ running: boolean; progress: number; logs: string[]; startedAt: string | null; status?: 'running' | 'success' | 'partial' | 'failed'; operation?: 'rollback'; params?: { migrationId: string }; completedSteps?: string[] }> => {
    const res = await api.get('/switchsemester/rollback/progress', { params: { guildId } });
    return res.data;
  },

  runRollback: async (
    guildId: string, migrationId: string, roleGroupKeys: string[], visibilityIds: number[], revertPlanPosition: boolean
  ): Promise<{ started: boolean }> => {
    const res = await api.post('/switchsemester/rollback', { guildId, migrationId, roleGroupKeys, visibilityIds, revertPlanPosition });
    return res.data;
  },

  getDiscordGuilds: async (): Promise<DiscordGuild[]> => {
    const res = await api.get('/discord/guilds');
    return res.data;
  },

  getDiscordApiStatus: async (): Promise<{ indicator: string | null; description: string | null }> => {
    const res = await api.get('/discord/status');
    return res.data;
  },

  getDiscordCategories: async (guildId: string): Promise<{ id: string; name: string; position: number }[]> => {
    const res = await api.get('/discord/categories', { params: { guildId } });
    return res.data;
  },

  getSemesterMappings: async (guildId: string): Promise<Record<number, Record<number, { id: string; name: string }[]>>> => {
    const res = await api.get('/semester/mappings', { params: { guildId } });
    return res.data;
  },

  saveSemesterMapping: async (guildId: string, rocnik: number, semester: number, categories: { id: string; name: string }[]): Promise<{ success: boolean }> => {
    const res = await api.post('/semester/mappings', { guildId, rocnik, semester, categories });
    return res.data;
  },

  setSemester: async (guildId: string, rocnik: number, semester: number, visible: boolean): Promise<{
    success: boolean;
    categoriesUpdated: number;
    channelsUpdated: number;
    rolesUpdated: number;
    logs: string[];
    error?: string;
  }> => {
    const res = await api.post('/semester', { guildId, rocnik, semester, visible });
    return res.data;
  },

  getAutoDeleteConfigs: async (guildId: string): Promise<AutoDeleteConfig[]> => {
    const res = await api.get('/autodelete', { params: { guildId } });
    return res.data;
  },

  createAutoDeleteConfig: async (guildId: string, data: Partial<AutoDeleteConfig>): Promise<AutoDeleteConfig> => {
    const res = await api.post('/autodelete', { guildId, ...data });
    return res.data;
  },

  updateAutoDeleteConfig: async (id: number, guildId: string, data: Partial<AutoDeleteConfig>): Promise<AutoDeleteConfig> => {
    const res = await api.patch(`/autodelete/${id}`, { guildId, ...data });
    return res.data;
  },

  deleteAutoDeleteConfig: async (id: number, guildId: string): Promise<{ success: boolean }> => {
    const res = await api.delete(`/autodelete/${id}`, { params: { guildId } });
    return res.data;
  },

  getAutoDeleteEnabled: async (guildId: string): Promise<{ enabled: boolean }> => {
    const res = await api.get('/autodelete/enabled', { params: { guildId } });
    return res.data;
  },

  setAutoDeleteEnabled: async (guildId: string, enabled: boolean): Promise<{ success: boolean }> => {
    const res = await api.post('/autodelete/enabled', { guildId, enabled });
    return res.data;
  },

  getRoleMenus: async (guildId: string): Promise<RoleMenuConfig[]> => {
    const res = await api.get('/rolemenu', { params: { guildId } });
    return res.data;
  },

  createRoleMenu: async (guildId: string, data: Partial<RoleMenuConfig>): Promise<RoleMenuConfig> => {
    const res = await api.post('/rolemenu', { guildId, ...data });
    return res.data;
  },

  updateRoleMenu: async (id: number, guildId: string, data: Partial<RoleMenuConfig>): Promise<RoleMenuConfig> => {
    const res = await api.patch(`/rolemenu/${id}`, { guildId, ...data });
    return res.data;
  },

  deleteRoleMenu: async (id: number, guildId: string): Promise<{ success: boolean }> => {
    const res = await api.delete(`/rolemenu/${id}`, { params: { guildId } });
    return res.data;
  },

  repostRoleMenu: async (id: number, guildId: string): Promise<RoleMenuConfig> => {
    const res = await api.post(`/rolemenu/${id}/repost`, null, { params: { guildId } });
    return res.data;
  },

  getRoleMenuEnabled: async (guildId: string): Promise<{ enabled: boolean }> => {
    const res = await api.get('/rolemenu/enabled', { params: { guildId } });
    return res.data;
  },

  setRoleMenuEnabled: async (guildId: string, enabled: boolean): Promise<{ success: boolean }> => {
    const res = await api.post('/rolemenu/enabled', { guildId, enabled });
    return res.data;
  },

  getThesisCounters: async (guildId: string): Promise<ThesisCounterConfig[]> => {
    const res = await api.get('/thesiscounter', { params: { guildId } });
    return res.data;
  },

  createThesisCounter: async (guildId: string, data: { channel_id: string; label: string; target_date: string; name_format: string | null; today_format: string | null }): Promise<ThesisCounterConfig> => {
    const res = await api.post('/thesiscounter', { guildId, ...data });
    return res.data;
  },

  updateThesisCounter: async (id: number, guildId: string, data: { channel_id: string; label: string; target_date: string; name_format: string | null; today_format: string | null }): Promise<ThesisCounterConfig> => {
    const res = await api.patch(`/thesiscounter/${id}`, { guildId, ...data });
    return res.data;
  },

  deleteThesisCounter: async (id: number, guildId: string): Promise<{ success: boolean }> => {
    const res = await api.delete(`/thesiscounter/${id}`, { params: { guildId } });
    return res.data;
  },

  getThesisCounterEnabled: async (guildId: string): Promise<{ enabled: boolean }> => {
    const res = await api.get('/thesiscounter/enabled', { params: { guildId } });
    return res.data;
  },

  setThesisCounterEnabled: async (guildId: string, enabled: boolean): Promise<{ success: boolean }> => {
    const res = await api.post('/thesiscounter/enabled', { guildId, enabled });
    return res.data;
  },
};
