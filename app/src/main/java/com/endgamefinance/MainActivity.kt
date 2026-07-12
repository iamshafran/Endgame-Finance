package com.endgamefinance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.endgamefinance.ui.navigation.EndgameApp
import com.endgamefinance.ui.theme.EndgameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EndgameTheme {
                EndgameApp()
            }
        }
    }
}
