package com.endgamefinance.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.endgamefinance.data.backup.BackupManager
import com.endgamefinance.data.backup.WrongPasswordException
import com.endgamefinance.data.db.DatabaseProvider
import com.endgamefinance.data.db.EndgameDatabase
import com.endgamefinance.security.BackupPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecurityViewModel(
    private val db: EndgameDatabase,
    private val appContext: Context,
) : ViewModel() {

    private val manager = BackupManager(db)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    fun exportCsv(uri: Uri) {
        run("CSV exported. Reminder: this file is NOT encrypted.") {
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                manager.exportCsv(out)
            } ?: error("Couldn't open the selected location")
        }
    }

    fun writeBackup(uri: Uri, password: CharArray) {
        run("Encrypted backup saved.") {
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                manager.writeEncryptedBackup(out, password)
            } ?: error("Couldn't open the selected location")
            BackupPrefs.markBackupNow(appContext)
        }
    }

    fun restoreBackup(uri: Uri, password: CharArray) {
        run("Backup restored. All data replaced from the backup file.") {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                manager.restoreEncryptedBackup(input, password)
            } ?: error("Couldn't open the selected file")
        }
    }

    private fun run(successMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                withContext(Dispatchers.IO) { block() }
                _message.value = successMessage
            } catch (e: WrongPasswordException) {
                _message.value = "Wrong password — nothing was changed."
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message ?: e.javaClass.simpleName}. " +
                    "No partial changes were made."
            } finally {
                _busy.value = false
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SecurityViewModel(DatabaseProvider.get(context), context.applicationContext)
            }
        }
    }
}
