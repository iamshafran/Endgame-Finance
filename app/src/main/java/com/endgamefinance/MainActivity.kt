package com.endgamefinance

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import com.endgamefinance.security.AppLock
import com.endgamefinance.security.AppSettings
import com.endgamefinance.ui.lock.LockScreen
import com.endgamefinance.ui.navigation.EndgameApp
import com.endgamefinance.ui.onboarding.OnboardingGate
import com.endgamefinance.ui.theme.EndgameTheme

// FragmentActivity (not ComponentActivity) because androidx.biometric requires it
class MainActivity : FragmentActivity() {

    private val locked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings = AppSettings.get(this)
            val themeMode by settings.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_DARK, AppSettings.THEME_OLED -> true
                else -> isSystemInDarkTheme()
            }
            val oledBlack = themeMode == AppSettings.THEME_OLED
            // Push the chosen display currency into the formatter before children
            // compose, so every Money.format() in this pass uses the new symbol.
            val currencyCode by settings.currencyCode.collectAsState()
            com.endgamefinance.util.Money.currencyCode = currencyCode
            val paletteName by settings.palette.collectAsState()
            val palette = runCatching {
                com.endgamefinance.ui.theme.ThemePalette.valueOf(paletteName)
            }.getOrDefault(com.endgamefinance.ui.theme.ThemePalette.DEFAULT)
            val fontKey by settings.fontKey.collectAsState()
            val font = com.endgamefinance.ui.theme.AppFont.fromKey(fontKey)
            EndgameTheme(
                palette = palette, font = font,
                darkTheme = darkTheme, oledBlack = oledBlack,
            ) {
                // Root Surface so every branch (lock, onboarding, app) inherits the
                // themed background AND onSurface content color — a bare MaterialTheme
                // does not set content color, only a Surface does.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val isLocked by locked
                    if (isLocked) {
                        LockScreen(onUnlock = ::showUnlockPrompt)
                    } else {
                        OnboardingGate { EndgameApp() }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (AppLock.requiresUnlock(this)) {
            locked.value = true
            showUnlockPrompt()
        }
    }

    override fun onStop() {
        super.onStop()
        AppLock.noteBackgrounded()
    }

    private fun showUnlockPrompt() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    AppLock.markUnlocked()
                    locked.value = false
                }
                // On error/cancel the lock screen stays; the Unlock button retries
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Endgame Finance")
                .setSubtitle("Biometric or device PIN")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build(),
        )
    }
}
