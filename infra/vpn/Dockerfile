FROM alpine:3.22

RUN apk add --no-cache ca-certificates openvpn openldap-clients socat

COPY entrypoint.sh /usr/local/bin/vpn-ldap-entrypoint
COPY healthcheck.sh /usr/local/bin/vpn-ldap-healthcheck
RUN chmod 0755 /usr/local/bin/vpn-ldap-entrypoint
RUN chmod 0755 /usr/local/bin/vpn-ldap-healthcheck

ENTRYPOINT ["/usr/local/bin/vpn-ldap-entrypoint"]
