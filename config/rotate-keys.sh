#!/usr/bin/env bash
set -euo pipefail

# Ultra Multi VPN — WireGuard Key Revocation & Regeneration Script
# Run this directly on your WireGuard VPN server (NOT inside a public Git repo).
# Requires: wireguard-tools (`wg`) and optionally `qrencode`.

OUT_DIR="${1:-/etc/wireguard/rotated}"
SERVER_ENDPOINT="${2:-vpn.yourdomain.com:51820}"
CLIENT_COUNT="${3:-10}"

umask 077
mkdir -p "$OUT_DIR/clients"

# 1. Generate brand new server keypair
SERVER_PRIV=$(wg genkey)
SERVER_PUB=$(printf '%s' "$SERVER_PRIV" | wg pubkey)

cat > "$OUT_DIR/wg0.conf" <<EOF
[Interface]
PrivateKey = $SERVER_PRIV
Address = 10.0.0.1/24
ListenPort = 51820
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o eth0 -j MASQUERADE
EOF

echo "Generated new server public key: $SERVER_PUB"

# 2. Regenerate fresh client keypairs & preshared keys (revoking all old peers)
for i in $(seq 1 "$CLIENT_COUNT"); do
  IP_OCTET=$((i + 1))
  CLIENT_PRIV=$(wg genkey)
  CLIENT_PUB=$(printf '%s' "$CLIENT_PRIV" | wg pubkey)
  CLIENT_PSK=$(wg genpsk)

  cat >> "$OUT_DIR/wg0.conf" <<EOF

[Peer]
# Client $i (rotated)
PublicKey = $CLIENT_PUB
PresharedKey = $CLIENT_PSK
AllowedIPs = 10.0.0.${IP_OCTET}/32
EOF

  cat > "$OUT_DIR/clients/client${i}.conf" <<EOF
[Interface]
PrivateKey = $CLIENT_PRIV
Address = 10.0.0.${IP_OCTET}/32
DNS = 1.1.1.1, 1.0.0.1
MTU = 1280

[Peer]
PublicKey = $SERVER_PUB
PresharedKey = $CLIENT_PSK
Endpoint = $SERVER_ENDPOINT
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
EOF
done

chmod 600 "$OUT_DIR/wg0.conf" "$OUT_DIR"/clients/*.conf
echo "Rotation complete in $OUT_DIR. Reload WireGuard with: wg syncconf wg0 <(wg-quick strip $OUT_DIR/wg0.conf)"
