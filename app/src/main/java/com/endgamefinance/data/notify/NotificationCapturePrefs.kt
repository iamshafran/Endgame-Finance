package com.endgamefinance.data.notify

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Consent + per-source settings for the notification capture feature
 * (Milestone 8.1). Nothing is scraped unless the user has (a) granted the OS
 * notification-listener permission, (b) flipped the master switch here, and
 * (c) explicitly whitelisted the specific source app. Auto-post is a further,
 * per-app opt-in on top of that — off by default, so every captured
 * transaction needs a tap to confirm.
 *
 * All state is local SharedPreferences; none of it leaves the device.
 */
object NotificationCapturePrefs {

    private const val PREFS = "notify_capture_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CONSENTED = "consented"
    private const val KEY_WHITELIST = "whitelist"      // set of package names
    private const val KEY_AUTOPOST = "autopost"        // set of package names

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var enabledFlow: MutableStateFlow<Boolean>? = null
    private var whitelistFlow: MutableStateFlow<Set<String>>? = null
    private var autoPostFlow: MutableStateFlow<Set<String>>? = null

    /** Master switch. Even when true, capture only runs for whitelisted apps and
     *  only while the OS listener permission is granted. */
    fun enabled(context: Context): StateFlow<Boolean> {
        enabledFlow?.let { return it }
        return MutableStateFlow(prefs(context).getBoolean(KEY_ENABLED, false))
            .also { enabledFlow = it }
    }

    fun isEnabled(context: Context): Boolean = enabled(context).value

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
        enabledFlow?.value = value
    }

    /** Whether the user has seen and accepted the consent explanation. */
    fun hasConsented(context: Context): Boolean = prefs(context).getBoolean(KEY_CONSENTED, false)

    fun setConsented(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_CONSENTED, value).apply()
    }

    fun whitelist(context: Context): StateFlow<Set<String>> {
        whitelistFlow?.let { return it }
        return MutableStateFlow(prefs(context).getStringSet(KEY_WHITELIST, emptySet())!!.toSet())
            .also { whitelistFlow = it }
    }

    fun isWhitelisted(context: Context, pkg: String): Boolean =
        whitelist(context).value.contains(pkg)

    fun setWhitelisted(context: Context, pkg: String, value: Boolean) {
        val next = whitelist(context).value.toMutableSet().apply {
            if (value) add(pkg) else remove(pkg)
        }
        prefs(context).edit().putStringSet(KEY_WHITELIST, next).apply()
        whitelistFlow?.value = next
        if (!value) setAutoPost(context, pkg, false) // can't auto-post a de-whitelisted source
    }

    fun autoPost(context: Context): StateFlow<Set<String>> {
        autoPostFlow?.let { return it }
        return MutableStateFlow(prefs(context).getStringSet(KEY_AUTOPOST, emptySet())!!.toSet())
            .also { autoPostFlow = it }
    }

    fun isAutoPost(context: Context, pkg: String): Boolean =
        autoPost(context).value.contains(pkg)

    fun setAutoPost(context: Context, pkg: String, value: Boolean) {
        val next = autoPost(context).value.toMutableSet().apply {
            if (value) add(pkg) else remove(pkg)
        }
        prefs(context).edit().putStringSet(KEY_AUTOPOST, next).apply()
        autoPostFlow?.value = next
    }

    /** The gate the listener consults for every notification. */
    fun shouldCapture(context: Context, pkg: String): Boolean =
        isEnabled(context) && isWhitelisted(context, pkg)
}
