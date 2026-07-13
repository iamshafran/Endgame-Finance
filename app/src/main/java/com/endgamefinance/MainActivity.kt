package com.endgamefinance

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
            val themeMode by AppSettings.get(this).themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            EndgameTheme(darkTheme = darkTheme) {
                val isLocked by locked
                if (isLocked) {
                    LockScreen(onUnlock = ::showUnlockPrompt)
                } else {
                    OnboardingGate { EndgameApp() }
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
