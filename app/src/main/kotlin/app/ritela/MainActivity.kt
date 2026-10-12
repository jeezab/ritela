package app.ritela

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.ritela.ui.ProfileApp

class MainActivity : ComponentActivity() {
    private val files = app.ritela.ui.PartnerFileRequest()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        files.inspect(intent)
        setContent {
            CompositionLocalProvider(app.ritela.ui.LocalPartnerFileRequest provides files) {
                ProfileApp()
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        files.inspect(intent)
    }
}
