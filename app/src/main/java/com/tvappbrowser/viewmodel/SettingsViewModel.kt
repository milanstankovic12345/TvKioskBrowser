package com.tvappbrowser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tvappbrowser.model.AppSettings
import com.tvappbrowser.repository.SettingsRepository

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    private val _settings = MutableLiveData<AppSettings>()
    val settings: LiveData<AppSettings> = _settings

    init {
        loadSettings()
    }

    fun loadSettings() {
        _settings.value = repository.getSettings()
    }

    fun saveSettings(newSettings: AppSettings) {
        repository.saveSettings(newSettings)
        _settings.value = newSettings
    }
    
    fun resetToDefaults() {
        val defaults = AppSettings()
        saveSettings(defaults)
    }
}
