package com.ridesync.app

import android.app.Application
import com.ridesync.app.core.CrashGuard
import com.ridesync.app.data.preferences.ProfileRepository
import com.ridesync.app.data.preferences.RecentRidesRepository
import com.ridesync.app.data.preferences.SettingsRepository
import com.ridesync.app.domain.session.SessionEnvironment
import com.ridesync.app.domain.session.SessionManager
import com.ridesync.app.license.LicenseManager

/**
 * Manual dependency container. RideSync has few, long-lived singletons and no
 * need for a DI framework, so they're built here and read via [container].
 */
class RideSyncApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Install the crash safety net FIRST, before anything else can fail.
        CrashGuard.install(this)
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val settingsRepository = SettingsRepository(app)
    val profileRepository = ProfileRepository(app)
    val recentRidesRepository = RecentRidesRepository(app)
    val licenseManager = LicenseManager(app)

    val environment = SessionEnvironment(
        appContext = app,
        settingsRepository = settingsRepository,
        profileRepository = profileRepository,
        recentRidesRepository = recentRidesRepository,
        licenseManager = licenseManager,
    )

    val sessionManager = SessionManager(environment)
}
