package com.ridesync.app.license

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ridesync.app.core.RLog
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.licenseDataStore: DataStore<Preferences> by preferencesDataStore(name = "license")

/**
 * Owns the device's license: activation, caching, revalidation, and the rider
 * cap the rest of the app enforces.
 *
 * The website ([LicenseConfig]) is the source of truth. We validate a key over
 * the internet once, cache the entitlement, and from then on the app works
 * offline — a ride never needs the network. Paid plans that lapse fall back to
 * Free (4 riders) automatically, computed from the cached expiry even offline.
 */
class LicenseManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> RLog.e(RLog.Cat.SESSION, "license coroutine failed", e) },
    ),
) {
    private object Keys {
        val KEY = stringPreferencesKey("lic_key")
        val PLAN_TIER = stringPreferencesKey("lic_plan_tier")
        val STATUS = stringPreferencesKey("lic_status")
        val EXPIRES_AT = longPreferencesKey("lic_expires_at") // epoch ms, -1 = none
        val LAST_VALIDATED = longPreferencesKey("lic_last_validated")
        val DEVICE_ID = stringPreferencesKey("lic_device_id")
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow(LicenseState())
    val state: StateFlow<LicenseState> = _state

    /** The rider cap in force right now (defaults to Free = 4 until loaded). */
    val maxRiders: Int get() = _state.value.maxRiders

    init {
        scope.launch {
            loadFromDisk()
            // Best-effort background refresh so renewals/lapses are picked up.
            if (_state.value.hasKey) refresh(force = false)
        }
    }

    private suspend fun loadFromDisk() {
        val p = context.licenseDataStore.data.first()
        val key = p[Keys.KEY] ?: ""
        if (key.isBlank()) {
            _state.value = LicenseState()
            return
        }
        val expires = p[Keys.EXPIRES_AT] ?: -1L
        _state.value = LicenseState(
            key = key,
            planTier = Tier.fromId(p[Keys.PLAN_TIER]),
            status = runCatching { LicenseState.Status.valueOf(p[Keys.STATUS] ?: "FREE") }
                .getOrDefault(LicenseState.Status.FREE),
            expiresAtMs = if (expires > 0) expires else null,
            lastValidatedMs = p[Keys.LAST_VALIDATED] ?: 0L,
        )
    }

    private suspend fun deviceId(): String {
        context.licenseDataStore.data.first()[Keys.DEVICE_ID]?.let { return it }
        val id = UUID.randomUUID().toString()
        context.licenseDataStore.edit { it[Keys.DEVICE_ID] = id }
        return id
    }

    /**
     * Activate a license key the user pasted from their dashboard. Returns true
     * on success. On failure, [state].lastError explains why.
     */
    suspend fun activate(rawKey: String): Boolean {
        val key = rawKey.trim().uppercase()
        if (key.isBlank()) {
            _state.value = _state.value.copy(lastError = "Enter your license key.")
            return false
        }
        _state.value = _state.value.copy(activating = true, lastError = null)
        val result = validateRemote(key)
        return applyResult(key, result, isActivation = true)
    }

    /** Re-check the cached key (renewals, lapses). Keeps the cache on failure. */
    suspend fun refresh(force: Boolean = true) {
        val current = _state.value
        if (!current.hasKey) return
        val stale = System.currentTimeMillis() - current.lastValidatedMs > LicenseConfig.REVALIDATE_INTERVAL_MS
        if (!force && !stale) return
        val result = validateRemote(current.key)
        applyResult(current.key, result, isActivation = false)
    }

    /** Remove the key from this device (drops back to Free). */
    suspend fun signOut() {
        context.licenseDataStore.edit { it.clear() }
        _state.value = LicenseState()
    }

    private suspend fun applyResult(key: String, result: ValidateResult, isActivation: Boolean): Boolean =
        when (result) {
            is ValidateResult.Ok -> {
                val r = result.response
                if (!r.valid) {
                    _state.value = _state.value.copy(
                        activating = false,
                        lastError = r.error ?: "That license key wasn't recognised.",
                    )
                    false
                } else {
                    val plan = Tier.fromId(r.planTier.ifBlank { r.tier })
                    val expires = parseIso(r.expiresAt)
                    persist(key, plan, r.status, expires)
                    _state.value = LicenseState(
                        key = key,
                        planTier = plan,
                        status = statusFrom(r.status),
                        expiresAtMs = expires,
                        lastValidatedMs = System.currentTimeMillis(),
                        activating = false,
                        lastError = null,
                        lastValidationFailed = false,
                    )
                    RLog.i(RLog.Cat.SESSION, "license validated: ${plan.displayName} (${_state.value.maxRiders} riders)")
                    true
                }
            }
            is ValidateResult.NotFound -> {
                _state.value = _state.value.copy(
                    activating = false,
                    lastError = "That license key wasn't recognised. Check it on your dashboard.",
                )
                false
            }
            is ValidateResult.Network -> {
                // Offline or server unreachable. On activation this is a failure;
                // on a background refresh we keep the cached entitlement.
                _state.value = _state.value.copy(
                    activating = false,
                    lastValidationFailed = true,
                    lastError = if (isActivation) result.message else _state.value.lastError,
                )
                RLog.w(RLog.Cat.SESSION, "license validation network error: ${result.message}")
                false
            }
        }

    private suspend fun persist(key: String, plan: Tier, status: String, expires: Long?) {
        context.licenseDataStore.edit {
            it[Keys.KEY] = key
            it[Keys.PLAN_TIER] = plan.id
            it[Keys.STATUS] = statusFrom(status).name
            it[Keys.EXPIRES_AT] = expires ?: -1L
            it[Keys.LAST_VALIDATED] = System.currentTimeMillis()
        }
    }

    private fun statusFrom(s: String): LicenseState.Status = when (s.lowercase()) {
        "active" -> LicenseState.Status.ACTIVE
        "expired" -> LicenseState.Status.EXPIRED
        "free" -> LicenseState.Status.FREE
        else -> LicenseState.Status.UNKNOWN
    }

    private fun parseIso(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        // Server sends ISO-8601 UTC like 2026-10-17T19:25:16.890Z.
        return runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
    }

    // ------------------------------------------------------- networking

    private sealed interface ValidateResult {
        data class Ok(val response: LicenseResponse) : ValidateResult
        object NotFound : ValidateResult
        data class Network(val message: String) : ValidateResult
    }

    private suspend fun validateRemote(key: String): ValidateResult = withContext(Dispatchers.IO) {
        if (LicenseConfig.SERVER_BASE_URL.contains("your-ridesync-server.example")) {
            return@withContext ValidateResult.Network(
                "No server set yet. Set SERVER_BASE_URL in LicenseConfig.kt to your website.",
            )
        }
        var conn: HttpURLConnection? = null
        try {
            val url = URL(LicenseConfig.validateUrl())
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            val body = json.encodeToString(
                ValidateRequest.serializer(),
                ValidateRequest(key = key, deviceId = deviceId()),
            )
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            when {
                code == 404 -> ValidateResult.NotFound
                code in 200..299 && text.isNotBlank() ->
                    ValidateResult.Ok(json.decodeFromString(LicenseResponse.serializer(), text))
                else -> ValidateResult.Network("Server error ($code). Try again.")
            }
        } catch (e: Exception) {
            ValidateResult.Network(e.message ?: "Couldn't reach the server. Check your connection.")
        } finally {
            conn?.disconnect()
        }
    }
}

@Serializable
private data class ValidateRequest(val key: String, val deviceId: String)

@Serializable
private data class LicenseResponse(
    val valid: Boolean = false,
    val key: String = "",
    val tier: String = "free",
    val tierName: String = "Free",
    val maxUsers: Int = 4,
    val status: String = "free",
    val expiresAt: String? = null,
    val planTier: String = "free",
    val error: String? = null,
)
