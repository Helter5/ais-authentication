#!/usr/bin/env node
/**
 * One-off export: reads the old Node/SQLite bot's database.db and emits a Postgres-compatible
 * .sql file of INSERT statements for the new Spring Boot app's schema.
 *
 * Scope (deliberately NOT everything - see chat for why):
 *   verified_users, warns, warn_thresholds, guild_settings, autodelete_configs, auto_mentions,
 *   incident_tickets, and exactly one admin_settings key ('allowed_guild_ids').
 * Explicitly skipped: sessions, access_logs, audit_logs, verification_codes (ephemeral),
 * reaction_roles (redesigned feature, not a 1:1 port - recreate manually via dashboard),
 * news_settings (feature doesn't exist in the new app), every other admin_settings key
 * (command-permission/progress blobs - either regenerable or keyed differently in the rewrite).
 *
 * Usage (run on the server, where better-sqlite3 is already installed for the old app):
 *   node migrate-export.js /opt/app/src/database/database.db > migration.sql
 *
 * Then REVIEW migration.sql before applying it. Apply with:
 *   docker exec -i <postgres-container> psql -U <db_user> -d <db_name> < migration.sql
 * (container name/user/db come from infra/.env in the new repo - DB_USER/DB_NAME, and
 * `docker ps` on the server for the actual postgres container name, e.g. ais-auth-postgres).
 */
const path = require('path');
const dbPath = process.argv[2];
if (!dbPath) {
    console.error('Usage: node migrate-export.js <path-to-database.db>');
    process.exit(1);
}

const Database = require(path.join(process.cwd(), 'node_modules', 'better-sqlite3'));
const db = new Database(dbPath, { readonly: true });

const out = [];
const log = (s) => out.push(s);

// ---- escaping helpers ----
function sqlStr(v) {
    if (v === null || v === undefined) return 'NULL';
    return `'${String(v).replace(/'/g, "''")}'`;
}
function sqlBool(v) {
    if (v === null || v === undefined) return 'NULL';
    return v ? 'TRUE' : 'FALSE';
}
function sqlJson(text, fallback) {
    if (text === null || text === undefined) {
        return fallback !== undefined ? `'${fallback}'::jsonb` : 'NULL';
    }
    try {
        JSON.parse(text); // validate before trusting it into ::jsonb
    } catch {
        console.error(`WARNING: invalid JSON skipped, using fallback: ${text}`);
        return fallback !== undefined ? `'${fallback}'::jsonb` : 'NULL';
    }
    return `${sqlStr(text)}::jsonb`;
}
function sqlNum(v) {
    return v === null || v === undefined ? 'NULL' : Number(v);
}

log('BEGIN;');
log('');

// ---- verified_users (id, ais_id, discord_id, guild_id, email, verified_at) - 1:1 ----
{
    const rows = db.prepare('SELECT * FROM verified_users').all();
    log(`-- verified_users: ${rows.length} row(s)`);
    for (const r of rows) {
        log(`INSERT INTO verified_users (id, ais_id, discord_id, guild_id, email, verified_at) VALUES ` +
            `(${sqlNum(r.id)}, ${sqlStr(r.ais_id)}, ${sqlStr(r.discord_id)}, ${sqlStr(r.guild_id)}, ${sqlStr(r.email)}, ${sqlStr(r.verified_at)}) ` +
            `ON CONFLICT DO NOTHING;`);
    }
    log('');
}

// ---- warns (id, guild_id, discord_id, moderator_id, reason, created_at) - 1:1 ----
{
    const rows = db.prepare('SELECT * FROM warns').all();
    log(`-- warns: ${rows.length} row(s)`);
    for (const r of rows) {
        log(`INSERT INTO warns (id, guild_id, discord_id, moderator_id, reason, created_at) VALUES ` +
            `(${sqlNum(r.id)}, ${sqlStr(r.guild_id)}, ${sqlStr(r.discord_id)}, ${sqlStr(r.moderator_id)}, ${sqlStr(r.reason)}, ${sqlStr(r.created_at)}) ` +
            `ON CONFLICT DO NOTHING;`);
    }
    log('');
}

// ---- warn_thresholds (old has no id - new autogenerates it) ----
{
    const rows = db.prepare('SELECT * FROM warn_thresholds').all();
    log(`-- warn_thresholds: ${rows.length} row(s)`);
    for (const r of rows) {
        log(`INSERT INTO warn_thresholds (guild_id, warn_limit, action) VALUES ` +
            `(${sqlStr(r.guild_id)}, ${sqlNum(r.warn_limit)}, ${sqlStr(r.action)}) ` +
            `ON CONFLICT (guild_id, warn_limit) DO NOTHING;`);
    }
    log('');
}

// ---- guild_settings (verification_enabled is INTEGER 0/1 in old -> BOOLEAN in new) ----
// V12__log_channel_subscriptions.sql (new schema) dropped log_channel_id/warn_log_channel_id/
// spam_log_channel_id/transcript_log_channel_id from this table - those 4 now backfill
// log_channel_subscription below instead, using V12's own event_type mapping.
{
    const rows = db.prepare('SELECT * FROM guild_settings').all();
    log(`-- guild_settings: ${rows.length} row(s)`);
    for (const r of rows) {
        log(`INSERT INTO guild_settings (guild_id, verified_role_id, inactive_role_id, spam_trap_channel_id, ` +
            `spam_delete_interval, verification_enabled, created_at, updated_at) VALUES ` +
            `(${sqlStr(r.guild_id)}, ${sqlStr(r.verified_role_id)}, ${sqlStr(r.inactive_role_id)}, ${sqlStr(r.spam_trap_channel_id)}, ` +
            `${sqlNum(r.spam_delete_interval ?? 60)}, ${sqlBool(r.verification_enabled ?? 1)}, ${sqlStr(r.created_at)}, ${sqlStr(r.updated_at)}) ` +
            `ON CONFLICT (guild_id) DO NOTHING;`);
    }
    log('');

    log(`-- log_channel_subscription backfill (mirrors V12's own migration-time mapping)`);
    const warnEvents = ['WARN_ISSUED', 'WARN_REMOVED', 'WARNS_CLEARED', 'WARN_THRESHOLD_ACTION'];
    for (const r of rows) {
        if (r.log_channel_id) {
            log(`INSERT INTO log_channel_subscription (guild_id, channel_id, event_type) VALUES ` +
                `(${sqlStr(r.guild_id)}, ${sqlStr(r.log_channel_id)}, 'WIPE_INACTIVE_USER_REMOVED') ` +
                `ON CONFLICT (guild_id, event_type) DO NOTHING;`);
        }
        if (r.warn_log_channel_id) {
            for (const eventType of warnEvents) {
                log(`INSERT INTO log_channel_subscription (guild_id, channel_id, event_type) VALUES ` +
                    `(${sqlStr(r.guild_id)}, ${sqlStr(r.warn_log_channel_id)}, '${eventType}') ` +
                    `ON CONFLICT (guild_id, event_type) DO NOTHING;`);
            }
        }
        if (r.spam_log_channel_id) {
            log(`INSERT INTO log_channel_subscription (guild_id, channel_id, event_type) VALUES ` +
                `(${sqlStr(r.guild_id)}, ${sqlStr(r.spam_log_channel_id)}, 'HACKED_ACCOUNT_TRAP_TRIGGERED') ` +
                `ON CONFLICT (guild_id, event_type) DO NOTHING;`);
        }
        if (r.transcript_log_channel_id) {
            log(`INSERT INTO log_channel_subscription (guild_id, channel_id, event_type) VALUES ` +
                `(${sqlStr(r.guild_id)}, ${sqlStr(r.transcript_log_channel_id)}, 'TICKET_TRANSCRIPT_SAVED') ` +
                `ON CONFLICT (guild_id, event_type) DO NOTHING;`);
        }
    }
    log('');
}

// ---- autodelete_configs (ignore_bots dropped - new app hardcodes "always skip bots", not configurable) ----
{
    const rows = db.prepare('SELECT * FROM autodelete_configs').all();
    log(`-- autodelete_configs: ${rows.length} row(s)`);
    if (rows.some(r => r.ignore_bots === 0)) {
        log(`-- NOTE: at least one config had ignore_bots=0 (wanted bot messages auto-deleted too) -`);
        log(`-- the new app always skips bot authors unconditionally, that toggle no longer exists.`);
    }
    for (const r of rows) {
        log(`INSERT INTO autodelete_configs (id, guild_id, channel_id, delay_seconds, ignore_role_ids, ` +
            `ignore_user_ids, ignore_pinned, notify_user, notify_via, notify_message, notify_delete_bot_msg, ` +
            `notify_delete_delay, created_at) VALUES ` +
            `(${sqlNum(r.id)}, ${sqlStr(r.guild_id)}, ${sqlStr(r.channel_id)}, ${sqlNum(r.delay_seconds)}, ${sqlJson(r.ignore_role_ids, '[]')}, ` +
            `${sqlJson(r.ignore_user_ids, '[]')}, ${sqlBool(r.ignore_pinned)}, ${sqlBool(r.notify_user)}, ${sqlStr(r.notify_via)}, ${sqlStr(r.notify_message)}, ${sqlBool(r.notify_delete_bot_msg ?? 1)}, ` +
            `${sqlNum(r.notify_delete_delay ?? 5)}, ${sqlStr(r.created_at)}) ` +
            `ON CONFLICT DO NOTHING;`);
    }
    log('');
}

// ---- auto_mentions (old table name: auto_mention_configs, no id, has updated_at which new drops) ----
{
    const rows = db.prepare('SELECT * FROM auto_mention_configs').all();
    log(`-- auto_mentions (from old auto_mention_configs): ${rows.length} row(s)`);
    for (const r of rows) {
        log(`INSERT INTO auto_mentions (guild_id, channel_id, role_id, enabled, created_at) VALUES ` +
            `(${sqlStr(r.guild_id)}, ${sqlStr(r.channel_id)}, ${sqlStr(r.role_id)}, ${sqlBool(r.enabled ?? 1)}, ${sqlStr(r.created_at)}) ` +
            `ON CONFLICT (guild_id, channel_id) DO NOTHING;`);
    }
    log('');
}

// ---- incident_tickets (transcript: JSON text or NULL in old, JSONB in new; controls_message_id is new-only, left NULL) ----
{
    const rows = db.prepare('SELECT * FROM incident_tickets').all();
    log(`-- incident_tickets: ${rows.length} row(s)`);
    for (const r of rows) {
        log(`INSERT INTO incident_tickets (channel_id, guild_id, user_id, status, closed_by, closed_at, transcript, created_at) VALUES ` +
            `(${sqlStr(r.channel_id)}, ${sqlStr(r.guild_id)}, ${sqlStr(r.user_id)}, ${sqlStr(r.status)}, ${sqlStr(r.closed_by)}, ${sqlStr(r.closed_at)}, ${sqlJson(r.transcript)}, ${sqlStr(r.created_at)}) ` +
            `ON CONFLICT (channel_id) DO NOTHING;`);
    }
    log('');
}

// ---- admin_settings: only the one key that's identical in both apps ----
{
    const row = db.prepare("SELECT * FROM admin_settings WHERE key = 'allowed_guild_ids'").get();
    log(`-- admin_settings['allowed_guild_ids']: ${row ? 'found' : 'not found, skipping'}`);
    if (row) {
        log(`INSERT INTO admin_settings (key, value, updated_at) VALUES ` +
            `('allowed_guild_ids', ${sqlJson(row.value, '[]')}, ${sqlStr(row.updated_at)}) ` +
            `ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at;`);
    }
    log('');
}

// ---- resync sequences for tables where we inserted explicit ids ----
log(`-- keep BIGSERIAL sequences ahead of the migrated explicit ids, so future inserts don't collide`);
log(`SELECT setval(pg_get_serial_sequence('verified_users', 'id'), COALESCE((SELECT MAX(id) FROM verified_users), 1));`);
log(`SELECT setval(pg_get_serial_sequence('warns', 'id'), COALESCE((SELECT MAX(id) FROM warns), 1));`);
log(`SELECT setval(pg_get_serial_sequence('autodelete_configs', 'id'), COALESCE((SELECT MAX(id) FROM autodelete_configs), 1));`);
log('');
log('COMMIT;');

console.log(out.join('\n'));
db.close();
