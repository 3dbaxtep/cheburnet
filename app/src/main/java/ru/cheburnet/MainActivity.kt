package ru.cheburnet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.cheburnet.ui.CheburnetTheme
import ru.cheburnet.ui.HomeScreen

class MainActivity : ComponentActivity() {

    private val vm: CheckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val learning by vm.learning.collectAsStateWithLifecycle()
            CheburnetTheme {
                HomeScreen(
                    state = state,
                    learning = learning,
                    onCheck = vm::check,
                    onStartLearning = vm::startLearning,
                    onShare = { text ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        startActivity(Intent.createChooser(send, "Отправить отчёт"))
                    },
                )
            }
        }
    }
}
