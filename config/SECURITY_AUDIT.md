# WireGuard Key Exposure Audit & Remediation

## Finding
The imported repository previously contained `config/wg0.conf` and `config/client1.conf` through `config/client250.conf` with plaintext WireGuard `PrivateKey` and `PresharedKey` values committed to Git history.

## Action Taken in Repository
- Removed all 251 exposed configuration files containing committed private keys and preshared keys.
- Added sanitized templates (`wg0.conf.example`, `client.conf.example`) and `config/rotate-keys.sh`.
- Ignored `config/*.conf` in `.gitignore` to prevent accidental future commits of live key material.

## Required Server-Side Actions
1. **Revoke & Replace Server Keys**: Run `config/rotate-keys.sh` directly on your WireGuard VPS/server (outside Git) and restart or `wg syncconf` the interface so every previously committed key is invalidated.
2. **Purge Git History (Optional)**: If the repository is public and these keys were used in production, scrub `config/*.conf` from Git history (`git filter-repo`) and rotate all peers.
3. **In-App Key Hygiene**: In **Ultra Multi VPN**, clients generate their Curve25519 `KeyPair` on-device and only register/rotate the **public key** with your Cloudflare Worker management API.
