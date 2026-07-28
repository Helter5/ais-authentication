import axios from 'axios';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:3001/api';

const api = axios.create({ baseURL: API_URL, withCredentials: true });

api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      window.location.href = '/login';
    }
    if (err.response?.status === 403 && err.response?.data?.error === 'Forbidden: Manager access required') {
      localStorage.removeItem('selected_guild_id');
      window.location.href = '/login?error=access_revoked';
    }
    return Promise.reject(err);
  }
);

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
  settings: {
    nickname: string;
    timezone: string;
  };
  synchronization: {
    intervalDays: number;
    lastSync: string | null;
    nextSync: string | null;
  };
}

export interface HackedAccountTrapSettings {
  enabled: boolean;
  trapChannelId: string | null;
  action: 'timeout' | 'kick' | 'ban';
  timeoutMinutes: number;
  logChannelId: string | null;
  deleteTriggerMessage: boolean;
  deleteRecentMessages: boolean;
  cleanupMinutes: number;
  exemptRoleIds: string[];
  ignoreAdministrators: boolean;
  dmUser: boolean;
  dmMessage: string;
  reason: string;
  incidentChannelEnabled: boolean;
  incidentChannelCategoryId: string | null;
  incidentChannelClosedCategoryId: string | null;
  incidentChannelNameTemplate: string;
  incidentChannelIncludeUser: boolean;
  incidentChannelMessage: string;
  incidentChannelPostDmStatus: boolean;
  incidentChannelTagRoles: boolean;
  incidentChannelTagRoleIds: string[];
}

export interface TicketTranscriptMessage {
  authorId: string;
  authorTag: string;
  timestamp: string;
  content: string;
  attachments: { url: string; name: string }[];
}

export interface TicketTranscript {
  channelId: string;
  guildId: string;
  userId: string;
  status: 'open' | 'closed';
  closedBy: string | null;
  closedAt: string | null;
  createdAt: string;
  messages: TicketTranscriptMessage[];
}

export interface AutoDeleteConfig {
  id: number;
  guild_id: string;
  channel_id: string;
  delay_seconds: number;
  ignore_role_ids: string[];
  ignore_user_ids: string[];
  ignore_bots: boolean;
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

  getLogs: async (service: 'bot' | 'vpn' = 'bot'): Promise<{ logs: string }> => {
    const res = await api.get('/logs', { params: { service } });
    return res.data;
  },

  getDashboard: async (guildId: string): Promise<DashboardData> => {
    const res = await api.get('/dashboard', { params: { guildId } });
    return res.data;
  },

  updateDashboardSettings: async (
    guildId: string,
    settings: DashboardData['settings'],
  ): Promise<{ success: boolean; settings: DashboardData['settings'] }> => {
    const res = await api.post('/dashboard/settings', { guildId, ...settings });
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

  getAdminStatus: async (): Promise<{ uptime: number; guildCount: number; verifiedCount: number; activeCodesCount: number; totalWarns: number; nodeVersion: string; memoryMB: number }> => {
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

  getSettings: async (guildId: string): Promise<{
    verified_role_id: string | null;
    inactive_role_id: string | null;
    log_channel_id: string | null;
    warn_log_channel_id: string | null;
    spam_trap_channel_id: string | null;
    spam_log_channel_id: string | null;
    spam_delete_interval: number;
    verification_enabled: boolean;
    transcript_log_channel_id: string | null;
    log_channel_health: Record<'verification' | 'moderation' | 'automod' | 'transcript', {
      ok: boolean;
      configured: boolean;
      channelId?: string;
      channelName?: string;
      error: string | null;
    }>;
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

  getHackedAccountTrap: async (guildId: string): Promise<HackedAccountTrapSettings> => {
    const res = await api.get('/modules/hacked-account-trap', { params: { guildId } });
    return res.data;
  },

  saveHackedAccountTrap: async (guildId: string, settings: HackedAccountTrapSettings): Promise<HackedAccountTrapSettings> => {
    const res = await api.post('/modules/hacked-account-trap', { guildId, ...settings });
    return res.data;
  },

  getTicketTranscript: async (channelId: string, guildId: string): Promise<TicketTranscript> => {
    const res = await api.get(`/tickets/${channelId}`, { params: { guildId } });
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

  getAutoMentions: async (guildId: string): Promise<{ channel_id: string; role_id: string; enabled: boolean }[]> => {
    const res = await api.get('/auto-mentions', { params: { guildId } });
    return res.data;
  },

  addAutoMention: async (guildId: string, channel_id: string, role_id: string): Promise<{ success: boolean }> => {
    const res = await api.post('/auto-mentions', { guildId, channel_id, role_id });
    return res.data;
  },

  toggleAutoMention: async (guildId: string, channelId: string): Promise<{ success: boolean; enabled: boolean }> => {
    const res = await api.patch(`/auto-mentions/${channelId}`, { guildId });
    return res.data;
  },

  removeAutoMention: async (guildId: string, channelId: string): Promise<{ success: boolean }> => {
    const res = await api.delete(`/auto-mentions/${channelId}`, { params: { guildId } });
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

  getSemesterAccess: async (guildId: string): Promise<{ allowed: boolean; reason?: string }> => {
    const res = await api.get('/semester/access', { params: { guildId } });
    return res.data;
  },

  getRecapChannels: async (guildId: string): Promise<{
    wipeChannelId: string | null;
    semesterChannelId: string | null;
    health: Record<'wipe' | 'semester', { ok: boolean; configured: boolean; error: string | null }>;
  }> => {
    const res = await api.get('/recap-channels', { params: { guildId } });
    return res.data;
  },

  setRecapChannels: async (guildId: string, wipeChannelId: string | null, semesterChannelId: string | null): Promise<{ success: boolean }> => {
    const res = await api.post('/recap-channels', { guildId, wipeChannelId, semesterChannelId });
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

  getSwitchSemesterProgress: async (guildId: string): Promise<{ running: boolean; progress: number; logs: string[]; startedAt: string | null; status?: 'running' | 'success' | 'partial' | 'failed'; operation?: 'switch'; params?: { oldName: string; newName: string }; completedSteps?: string[] }> => {
    const res = await api.get('/switchsemester/progress', { params: { guildId } });
    return res.data;
  },

  runSwitchSemester: async (guildId: string, oldName: string, newName: string, resume = false): Promise<{ started: boolean }> => {
    const res = await api.post('/switchsemester/run', { guildId, oldName, newName, resume });
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

  getDiscordGuilds: async (): Promise<DiscordGuild[]> => {
    const res = await api.get('/discord/guilds');
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
};
