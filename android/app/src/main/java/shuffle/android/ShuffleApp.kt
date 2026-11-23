package shuffle.android

import android.app.Application
import shuffle.fwk.config.ConfigPaths

class ShuffleApp : Application() {

    lateinit var repo: ShuffleRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Shuffle Move keeps user config (teams, roster levels, preferences) in config/*.txt relative
        // to its base directory; on Android that is the app's private files dir.
        ConfigPaths.setBaseDirectory(filesDir)
        repo = ShuffleRepository(this)
    }
}

val android.content.Context.repo: ShuffleRepository
    get() = (applicationContext as ShuffleApp).repo
