package shuffle.android.ui

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import shuffle.android.overlay.OverlayService

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val screen = mutableStateOf(SCREEN_HOME)

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        if (res.resultCode == RESULT_OK && data != null) {
            OverlayService.start(this, res.resultCode, data)
            // Get out of the way so the user can switch to the game.
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "Screen capture is needed to read the board.", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestProjection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val running by OverlayService.running.collectAsState()
                    val current = screen.value
                    BackHandler(enabled = current != SCREEN_HOME) {
                        screen.value = if (current == SCREEN_CALIBRATE) SCREEN_REVIEW else SCREEN_HOME
                    }
                    when (current) {
                        SCREEN_SETUP -> SetupScreen(vm) { screen.value = SCREEN_HOME }
                        SCREEN_PRACTICE -> PracticeScreen(vm) { screen.value = SCREEN_HOME }
                        SCREEN_REVIEW -> ReviewScreen(
                            vm,
                            onBack = { screen.value = SCREEN_HOME },
                            onCalibrate = { screen.value = SCREEN_CALIBRATE },
                        )
                        SCREEN_CALIBRATE -> CalibrateScreen(vm) { screen.value = SCREEN_REVIEW }
                        else -> HomeScreen(
                            vm,
                            overlayRunning = running,
                            onStartOverlay = ::startOverlay,
                            onStopOverlay = { OverlayService.stop(this) },
                            onEditTeam = { screen.value = SCREEN_SETUP },
                            onPractice = { screen.value = SCREEN_PRACTICE },
                            onReview = { screen.value = SCREEN_REVIEW },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_SCREEN)?.let { screen.value = it }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow Shuffle Move to display over other apps, then tap Start again.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The foreground-service notification (with its Stop button) needs this on Android 13+.
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestProjection()
        }
    }

    private fun requestProjection() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_HOME = "home"
        const val SCREEN_SETUP = "setup"
        const val SCREEN_PRACTICE = "practice"
        const val SCREEN_REVIEW = "review"
        const val SCREEN_CALIBRATE = "calibrate"
    }
}
