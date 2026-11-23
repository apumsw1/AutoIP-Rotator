package com.example.autoiprotator.data

import com.example.autoiprotator.model.AuditEventType
import com.example.autoiprotator.model.ConnectionAuditLog
import com.example.autoiprotator.model.UserPreferences
import com.example.autoiprotator.model.VpnServerProfile
import kotlinx.coroutines.flow.Flow

interface VpnDataSource {
    fun observeUserPreferences(userId: String): Flow<UserPreferences?>
    suspend fun ensureUserPreferences(
        userId: String,
        displayName: String,
        email: String?
    ): Result<Unit>

    suspend fun updateWorkerSettings(
        userId: String,
        workerBaseUrl: String,
        autoSelectEnabled: Boolean,
        preferredCountryCode: String
    ): Result<Unit>

    fun observeServers(userId: String): Flow<List<VpnServerProfile>>
    suspend fun upsertServer(userId: String, server: VpnServerProfile): Result<String>
    suspend fun toggleFavorite(userId: String, serverId: String, favorite: Boolean): Result<Unit>
    suspend fun updateServerLatency(userId: String, serverId: String, latencyMs: Long): Result<Unit>
    suspend fun rotateClientKey(
        userId: String,
        serverId: String,
        newPrivateKey: String,
        newClientAddress: String?
    ): Result<Unit>

    suspend fun deleteServer(userId: String, serverId: String): Result<Unit>
    fun observeLogs(userId: String): Flow<List<ConnectionAuditLog>>
    suspend fun appendLog(
        userId: String,
        serverName: String,
        countryCode: String,
        verifiedIp: String,
        verifiedCountry: String,
        eventType: AuditEventType
    ): Result<Unit>
}
