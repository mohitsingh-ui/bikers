package com.ridesync.app.license

/**
 * Where the app finds your licensing website.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  CHANGE THIS to your deployed website before you ship the app.       │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 *  - After you deploy `ridesync-web` (e.g. to Render), set SERVER_BASE_URL to
 *    that HTTPS domain, e.g. "https://ridesync.onrender.com".
 *  - For local testing with the server on your computer, use your computer's
 *    LAN IP and port, e.g. "http://192.168.1.20:3000" (NOT "localhost" — that
 *    means the phone itself). To allow plain http in a debug build, the
 *    manifest sets android:usesCleartextTraffic="true".
 *
 * Validation needs internet ONCE. After a key is activated the entitlement is
 * cached, so rides keep working with no internet.
 */
object LicenseConfig {
    const val SERVER_BASE_URL = "https://your-ridesync-server.example"
    const val VALIDATE_PATH = "/api/license/validate"

    /** Where the Subscription screen sends people to buy or manage a plan. */
    const val MANAGE_URL = "$SERVER_BASE_URL/dashboard"

    /** Re-validate a cached key at most this often (24h) to catch renewals/lapses. */
    const val REVALIDATE_INTERVAL_MS = 24L * 60 * 60 * 1000

    fun validateUrl(): String = SERVER_BASE_URL.trimEnd('/') + VALIDATE_PATH
}
