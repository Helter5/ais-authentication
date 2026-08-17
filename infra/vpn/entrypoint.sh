#!/bin/sh
set -eu

VPN_CONFIG=/vpn/client.ovpn
VPN_AUTH_FILE=/run/vpn-auth-runtime
LDAP_LISTEN_PORT="${LDAP_PROXY_PORT:-1389}"
LDAP_TARGET_HOST="${LDAP_UPSTREAM_HOST:-ldap.stuba.sk}"
LDAP_TARGET_PORT="${LDAP_UPSTREAM_PORT:-389}"

if [ ! -r "$VPN_CONFIG" ]; then
    echo "VPN profile is missing: mount client.ovpn at $VPN_CONFIG" >&2
    exit 1
fi

if [ -z "${VPN_USERNAME:-}" ] || [ -z "${VPN_PASSWORD:-}" ]; then
    echo "VPN_USERNAME and VPN_PASSWORD must both be set" >&2
    exit 1
fi

# openvpn's --auth-user-pass has no native env-var mode, only a file - so this is written
# fresh from env vars at container start (VPN_AUTH_FILE lives on the /run tmpfs mount,
# memory-only, wiped with the container) instead of bind-mounting a host auth.txt file.
umask 077
printf '%s\n%s\n' "$VPN_USERNAME" "$VPN_PASSWORD" > "$VPN_AUTH_FILE"

cleanup() {
    [ -z "${SOCAT_PID:-}" ] || kill "$SOCAT_PID" 2>/dev/null || true
    [ -z "${OPENVPN_PID:-}" ] || kill "$OPENVPN_PID" 2>/dev/null || true
    rm -f "$VPN_AUTH_FILE"
}
trap cleanup EXIT INT TERM

openvpn --config "$VPN_CONFIG" --auth-user-pass "$VPN_AUTH_FILE" --auth-nocache &
OPENVPN_PID=$!

# Listen only after the process has started. Docker health checks verify that the
# upstream LDAP server is reachable through the established tunnel.
socat "TCP-LISTEN:${LDAP_LISTEN_PORT},fork,reuseaddr" "TCP:${LDAP_TARGET_HOST}:${LDAP_TARGET_PORT}" &
SOCAT_PID=$!

while kill -0 "$OPENVPN_PID" 2>/dev/null && kill -0 "$SOCAT_PID" 2>/dev/null; do
    sleep 2
done

echo "OpenVPN or the LDAP proxy stopped unexpectedly" >&2
exit 1
