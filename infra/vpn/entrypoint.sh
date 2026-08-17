#!/bin/sh
set -eu

VPN_CONFIG=/vpn/client.ovpn
VPN_AUTH_SECRET=/run/secrets/vpn_auth
LDAP_LISTEN_PORT="${LDAP_PROXY_PORT:-1389}"
LDAP_TARGET_HOST="${LDAP_UPSTREAM_HOST:-ldap.stuba.sk}"
LDAP_TARGET_PORT="${LDAP_UPSTREAM_PORT:-389}"

if [ ! -r "$VPN_CONFIG" ]; then
    echo "VPN profile is missing: mount client.ovpn at $VPN_CONFIG" >&2
    exit 1
fi

if [ ! -r "$VPN_AUTH_SECRET" ] || [ "$(wc -l < "$VPN_AUTH_SECRET")" -lt 2 ]; then
    echo "VPN auth secret must contain the username and password on separate lines" >&2
    exit 1
fi

cleanup() {
    [ -z "${SOCAT_PID:-}" ] || kill "$SOCAT_PID" 2>/dev/null || true
    [ -z "${OPENVPN_PID:-}" ] || kill "$OPENVPN_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

openvpn --config "$VPN_CONFIG" --auth-user-pass "$VPN_AUTH_SECRET" --auth-nocache &
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
