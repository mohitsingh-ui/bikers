package com.ridesync.app.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ridesync.app.core.RLog
import com.ridesync.app.domain.model.TrackInfo

/**
 * Boundary for pluggable music sources.
 *
 * RideSync synchronizes WHAT to play + WHEN + POSITION — not the audio itself.
 * For the demo source we own the files, so we can control playback position
 * exactly. For third-party services (Spotify, YouTube Music, local library),
 * capabilities vary and Android does not let one app scrub another app's player
 * frame-accurately or legally rebroadcast its audio.
 *
 * This interface makes that explicit: an integration declares whether it
 * supports precise position control. When it doesn't, the app degrades
 * honestly to "Host is playing X — [Open in music app]" instead of pretending
 * to sync.
 */
interface MediaIntegration {
    val id: String
    val displayName: String

    /** Can we read/seek playback position precisely enough to sync? */
    val supportsPreciseSync: Boolean

    fun availableTracks(): List<TrackInfo>
    fun canControlRemotely(): Boolean
}

/** Honest fallback launcher for services we can't scrub. */
object ExternalMusicLauncher {

    /** Best-effort deep link / search intent so a rider can open the same song. */
    fun openInMusicApp(context: Context, track: TrackInfo): Boolean {
        val query = "${track.title} ${track.artist}".trim()
        // Try a generic music search intent first.
        val searchIntent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
            putExtra("android.intent.extra.focus", "vnd.android.cursor.item/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(context, searchIntent)) return true

        // Fall back to a web music search the user can tap through.
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return tryStart(context, web)
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        RLog.d(RLog.Cat.MUSIC, "external launch failed: ${e.message}")
        false
    }
}
