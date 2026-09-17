package com.ridesync.app

import android.app.Application
import com.ridesync.app.data.preferences.ProfileRepository
import com.ridesync.app.data.preferences.RecentRidesRepository
import com.ridesync.app.data.preferences.SettingsRepository
import com.ridesync.app.domain.session.SessionEnvironment
import com.ridesync.app.domain.session.SessionManager

/**
 * Manual dependency container. RideSync has few, long-lived singletons and no
 * need for a DI framework, so they're built here and read via [container].
 */
class RideSyncApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val settingsRepository = SettingsRepository(app)
    val profileRepository = ProfileRepository(app)
    val recentRidesRepository = RecentRidesRepository(app)

    val environment = SessionEnvironment(
        appContext = app,
        settingsRepository = settingsRepository,
        profileRepository = profileRepository,
        recentRidesRepository = recentRidesRepository,
    )

    val sessionManager = SessionManager(environment)
}
