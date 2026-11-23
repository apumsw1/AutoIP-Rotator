package com.example.autoiprotator.data

import com.example.autoiprotator.model.AuditEventType
import com.example.autoiprotator.model.ConnectionAuditLog
import com.example.autoiprotator.model.UserPreferences
import com.example.autoiprotator.model.VpnServerProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale

class VpnRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : VpnDataSource {

    private fun requireUserId(expectedUid: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUid != null) {
            check(uid == expectedUid) { "Signed-in user changed; refusing cross-user access." }
        }
        return uid
    }

    private fun sanitizeDocId(rawId: String): String {
        val clean = rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        return clean.ifBlank { db.collection("tmp").document().id }
    }

    override fun observeUserPreferences(userId: String): Flow<UserPreferences?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(
                        UserPreferences::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun ensureUserPreferences(
        userId: String,
        displayName: String,
        email: String?
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            val safeName = displayName.trim().ifBlank { "VPN Operator" }.take(100)
            if (!existing.exists()) {
                val payload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to email?.takeIf { it.isNotBlank() }?.take(200),
                    "workerBaseUrl" to "",
                    "autoSelectEnabled" to true,
                    "preferredCountryCode" to "AUTO",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                @Suppress("UNCHECKED_CAST")
                docRef.set(payload as Map<String, Any>).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateWorkerSettings(
        userId: String,
        workerBaseUrl: String,
        autoSelectEnabled: Boolean,
        preferredCountryCode: String
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
            docRef.update(
                mapOf(
                    "workerBaseUrl" to workerBaseUrl.trim().take(500),
                    "autoSelectEnabled" to autoSelectEnabled,
                    "preferredCountryCode" to preferredCountryCode.trim().uppercase(Locale.US).ifBlank { "AUTO" }.take(12),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override fun observeServers(userId: String): Flow<List<VpnServerProfile>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/vpn_servers"
        emitAll(
            db.collection("users").document(uid).collection("vpn_servers")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VpnServerProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<VpnServerProfile> { it.favorite }
                            .thenBy { if (it.latencyMs < 0L) Long.MAX_VALUE else it.latencyMs }
                            .thenBy { it.countryName }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun upsertServer(userId: String, server: VpnServerProfile): Result<String> {
        val colPath = "users/$userId/vpn_servers"
        return try {
            val uid = requireUserId(userId)
            require(server.name.isNotBlank()) { "Server name is required." }
            require(server.endpoint.length >= 3) { "Endpoint is invalid." }
            require(server.serverPublicKey.length >= 40) { "Server public key is invalid." }
            require(server.clientPrivateKey.length >= 40) { "Client private key is invalid." }

            val colRef = db.collection("users").document(uid).collection("vpn_servers")
            val docRef = if (server.id.isNotBlank()) {
                colRef.document(sanitizeDocId(server.id))
            } else {
                colRef.document()
            }

            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mutableMapOf<String, Any?>(
                    "name" to server.name.take(100),
                    "countryCode" to server.countryCode.uppercase(Locale.US).take(8),
                    "countryName" to server.countryName.take(80),
                    "city" to server.city.take(80),
                    "endpoint" to server.endpoint.take(200),
                    "serverPublicKey" to server.serverPublicKey.take(64),
                    "clientAddress" to server.clientAddress.take(120),
                    "clientPrivateKey" to server.clientPrivateKey.take(64),
                    "presharedKey" to server.presharedKey?.takeIf { it.isNotBlank() }?.take(64),
                    "dns" to server.dns.take(120),
                    "allowedIps" to server.allowedIps.take(120),
                    "mtu" to server.mtu.coerceIn(1200L, 1500L),
                    "persistentKeepalive" to server.persistentKeepalive.coerceIn(0L, 120L),
                    "latencyMs" to server.latencyMs.coerceIn(-1L, 60000L),
                    "loadPercent" to server.loadPercent.coerceIn(0L, 100L),
                    "source" to server.source,
                    "favorite" to server.favorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                @Suppress("UNCHECKED_CAST")
                docRef.update(updates as Map<String, Any>).await()
            } else {
                docRef.set(server.toCreateMap(uid)).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, colPath)
            Result.failure(e)
        }
    }

    override suspend fun toggleFavorite(userId: String, serverId: String, favorite: Boolean): Result<Unit> {
        val path = "users/$userId/vpn_servers/$serverId"
        return try {
            val uid = requireUserId(userId)
            require(serverId.isNotBlank()) { "Server ID must not be blank." }
            db.collection("users").document(uid)
                .collection("vpn_servers").document(serverId)
                .update(
                    mapOf(
                        "favorite" to favorite,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateServerLatency(userId: String, serverId: String, latencyMs: Long): Result<Unit> {
        val path = "users/$userId/vpn_servers/$serverId"
        return try {
            val uid = requireUserId(userId)
            require(serverId.isNotBlank()) { "Server ID must not be blank." }
            db.collection("users").document(uid)
                .collection("vpn_servers").document(serverId)
                .update(
                    mapOf(
                        "latencyMs" to latencyMs.coerceIn(-1L, 60000L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun rotateClientKey(
        userId: String,
        serverId: String,
        newPrivateKey: String,
        newClientAddress: String?
    ): Result<Unit> {
        val path = "users/$userId/vpn_servers/$serverId"
        return try {
            val uid = requireUserId(userId)
            require(serverId.isNotBlank()) { "Server ID must not be blank." }
            require(newPrivateKey.length in 40..64) { "Invalid rotated private key." }
            val updateMap = mutableMapOf<String, Any>(
                "clientPrivateKey" to newPrivateKey,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!newClientAddress.isNullOrBlank()) {
                updateMap["clientAddress"] = newClientAddress.take(120)
            }
            db.collection("users").document(uid)
                .collection("vpn_servers").document(serverId)
                .update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteServer(userId: String, serverId: String): Result<Unit> {
        val path = "users/$userId/vpn_servers/$serverId"
        return try {
            val uid = requireUserId(userId)
            require(serverId.isNotBlank()) { "Server ID must not be blank." }
            db.collection("users").document(uid)
                .collection("vpn_servers").document(serverId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    override fun observeLogs(userId: String): Flow<List<ConnectionAuditLog>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/connection_logs"
        emitAll(
            db.collection("users").document(uid)
                .collection("connection_logs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(25)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ConnectionAuditLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun appendLog(
        userId: String,
        serverName: String,
        countryCode: String,
        verifiedIp: String,
        verifiedCountry: String,
        eventType: AuditEventType
    ): Result<Unit> {
        val path = "users/$userId/connection_logs"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
                .collection("connection_logs").document()
            val payload = mapOf(
                "userId" to uid,
                "serverName" to serverName.trim().ifBlank { "WireGuard Node" }.take(100),
                "countryCode" to countryCode.trim().uppercase(Locale.US).ifBlank { "XX" }.take(8),
                "verifiedIp" to verifiedIp.trim().take(64),
                "verifiedCountry" to verifiedCountry.trim().take(80),
                "eventType" to eventType.wireValue,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
