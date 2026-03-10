package de.manhhao.hoshi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.manhhao.hoshi.ui.Tabs
import de.manhhao.hoshi.ui.theme.HoshiReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HoshiReaderTheme {
                Tabs()
            }
        }
    }
}
