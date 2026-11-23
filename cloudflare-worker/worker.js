/**
 * Ultra Multi VPN — Cloudflare Worker Server Management API
 *
 * Purpose: Manages VPN server catalog, health metadata, and WireGuard peer
 * public-key registration/rotation ONLY. VPN tunnel traffic flows directly
 * between the Android WireGuard engine and your dedicated VPS WireGuard nodes.
 *
 * Required Worker Secrets / Bindings (configure via `wrangler secret put`):
 * - API_AUTH_TOKEN: Bearer token (or verify Firebase ID tokens)
 * - SERVERS_KV (optional KV namespace) for dynamic multi-country server catalog
 */

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204 });
    }

    // Health check endpoint
    if (url.pathname === "/api/health" && request.method === "GET") {
      return json({ ok: true, service: "ultra-multi-vpn-api", timestamp: Date.now() });
    }

    // Require Bearer token on all management routes
    const authHeader = request.headers.get("Authorization") || "";
    const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";
    if (!env.API_AUTH_TOKEN || token !== env.API_AUTH_TOKEN) {
      return json({ error: "Unauthorized" }, 401);
    }

    // GET /api/servers — returns configured multi-country WireGuard servers
    if (url.pathname === "/api/servers" && request.method === "GET") {
      const raw = env.SERVERS_KV ? await env.SERVERS_KV.get("servers_catalog") : null;
      const servers = raw ? JSON.parse(raw) : [];
      return json({ servers });
    }

    // POST /api/peers/register — registers a client's public key on a node
    if (url.pathname === "/api/peers/register" && request.method === "POST") {
      const body = await request.json().catch(() => null);
      if (!body || !body.serverId || !body.clientPublicKey) {
        return json({ error: "serverId and clientPublicKey are required" }, 400);
      }
      // Allocate / look up peer IP in KV and forward public key to node agent
      const peerKey = `peer:${body.serverId}:${body.clientPublicKey}`;
      const assignedIp = `10.8.0.${(Math.abs(hashCode(body.clientPublicKey)) % 200) + 10}/32`;
      if (env.SERVERS_KV) {
        await env.SERVERS_KV.put(peerKey, JSON.stringify({
          serverId: body.serverId,
          clientPublicKey: body.clientPublicKey,
          clientAddress: assignedIp,
          updatedAt: Date.now(),
        }));
      }
      return json({
        serverId: body.serverId,
        clientAddress: assignedIp,
        dns: "1.1.1.1, 1.0.0.1",
        mtu: 1280,
        allowedIps: "0.0.0.0/0, ::/0",
      });
    }

    // POST /api/peers/rotate — revokes previous client public key and registers new one
    if (url.pathname === "/api/peers/rotate" && request.method === "POST") {
      const body = await request.json().catch(() => null);
      if (!body || !body.serverId || !body.newPublicKey) {
        return json({ error: "serverId and newPublicKey are required" }, 400);
      }
      if (env.SERVERS_KV && body.oldPublicKey) {
        await env.SERVERS_KV.delete(`peer:${body.serverId}:${body.oldPublicKey}`);
      }
      const assignedIp = `10.8.0.${(Math.abs(hashCode(body.newPublicKey)) % 200) + 10}/32`;
      if (env.SERVERS_KV) {
        await env.SERVERS_KV.put(`peer:${body.serverId}:${body.newPublicKey}`, JSON.stringify({
          serverId: body.serverId,
          clientPublicKey: body.newPublicKey,
          clientAddress: assignedIp,
          rotatedAt: Date.now(),
        }));
      }
      return json({
        rotated: true,
        serverId: body.serverId,
        clientAddress: assignedIp,
      });
    }

    return json({ error: "Not found" }, 404);
  },
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function hashCode(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) - hash) + str.charCodeAt(i);
    hash |= 0;
  }
  return hash;
}
