package dev.kyle.mctools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.kyle.mctools.ui.ActivityRepo
import dev.kyle.mctools.ui.Prefs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ActivityRepo.init(applicationContext)
        val prefs = Prefs(applicationContext)
        setContent { KyleApp(prefs) }
    }
}
