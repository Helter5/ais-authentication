#!/bin/sh
set -eu

exec ldapsearch \
    -x \
    -H "ldap://127.0.0.1:${LDAP_PROXY_PORT:-1389}" \
    -o nettimeout=5 \
    -l 5 \
    -z 1 \
    -b "${LDAP_BASE:-ou=People,dc=stuba,dc=sk}" \
    -s base \
    '(objectClass=*)' dn >/dev/null 2>&1
