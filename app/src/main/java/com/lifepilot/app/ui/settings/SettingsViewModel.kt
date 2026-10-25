package com.lifepilot.app.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifepilot.app.AppContainer
import com.lifepilot.app.data.backup.BackupPreview
import com.lifepilot.app.domain.model.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _importPreview = MutableStateFlow<BackupPreview?>(null)
    val importPreview: StateFlow<BackupPreview?> = _importPreview.asStateFlow()

    fun consumeMessage() { _message.value = null }

    /**
     * Applies a settings change. Changes that affect reminder timing (master switch, quiet hours,
     * habit reminders) re-plan every alarm so the new rules take effect immediately.
     */
    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val before = container.settings.current()
            container.settings.update(transform)
            val after = container.settings.current()
            if (before.remindersEnabled != after.remindersEnabled || before.quietHours != after.quietHours ||
                before.habitRemindersEnabled != after.habitRemindersEnabled
            ) {
                runCatching { container.reconcileAll() }
            }
        }
    }

    fun exportJson(resolver: ContentResolver, uri: Uri) = write(resolver, uri, "Backup saved") { container.backupManager.exportJson() }
    fun exportTasksCsv(resolver: ContentResolver, uri: Uri) = write(resolver, uri, "Tasks exported") { container.backupManager.exportTasksCsv() }
    fun exportHabitsCsv(resolver: ContentResolver, uri: Uri) = write(resolver, uri, "Habits exported") { container.backupManager.exportHabitsCsv() }

    private fun write(resolver: ContentResolver, uri: Uri, success: String, content: suspend () -> String) {
        viewModelScope.launch {
            val result = runCatching {
                val text = content()
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("Could not open file")
                }
            }
            _message.value = if (result.isSuccess) success else "Export failed: ${result.exceptionOrNull()?.message}"
        }
    }

    /** Step 1 of import: read and validate without touching any data. */
    fun readBackup(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        require(bytes.size <= 50 * 1024 * 1024) { "File is too large" }
                        bytes.toString(Charsets.UTF_8)
                    } ?: error("Could not open file")
                }
            }
            text.onSuccess { _importPreview.value = container.backupManager.preview(it) }
                .onFailure { _message.value = "Could not read file: ${it.message}" }
        }
    }

    fun cancelImport() { _importPreview.value = null }

    /** Step 2 of import, after explicit confirmation: replace everything, then re-plan alarms. */
    fun confirmImport() {
        val data = _importPreview.value?.data ?: return
        _importPreview.value = null
        viewModelScope.launch {
            runCatching {
                container.backupManager.restore(data)
                container.reconcileAll()
            }.onSuccess { _message.value = "Backup restored. A safety copy of your previous data was kept on this device." }
                .onFailure { _message.value = "Restore failed, your data was not changed: ${it.message}" }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            runCatching {
                container.backupManager.clearAll()
                container.reconcileAll()
            }.onSuccess { _message.value = "All tasks and habits were deleted" }
                .onFailure { _message.value = "Could not clear data: ${it.message}" }
        }
    }
}
