package io.github.torintheone.petri

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.torintheone.petri.ui.PetriApp
import io.github.torintheone.petri.ui.theme.PetriTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PetriViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark, so the system bars always get light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) viewModel.handleLaunch(intent)
        setContent {
            PetriTheme {
                PetriApp(viewModel)
            }
        }
    }

    /** A launch with extras while the app is already open (see [PetriViewModel.handleLaunch]). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleLaunch(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppVisible(true)
    }

    override fun onStop() {
        viewModel.onAppVisible(false)
        super.onStop()
    }
}
