package com.smartvendor.ai.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartvendor.ai.model.CIEAlertModel
import com.smartvendor.ai.model.JournalTransaction
import com.smartvendor.ai.model.RecoveryReport
import com.smartvendor.ai.model.SystemStatus
import com.smartvendor.ai.repository.ResilienceRepository
import com.smartvendor.ai.repository.ResilienceRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResilienceViewModel(
    private val repository: ResilienceRepository = ResilienceRepositoryImpl()
) : ViewModel() {

    val systemStatus: StateFlow<SystemStatus> = repository.systemStatusFlow
    val journal: StateFlow<List<JournalTransaction>> = repository.journalFlow
    val latestReport: StateFlow<RecoveryReport?> = repository.latestReportFlow
    val cieAlert: StateFlow<CIEAlertModel?> = repository.cieAlertFlow
    val hasActiveCIEIncident: StateFlow<Boolean> = repository.hasActiveCIEIncidentFlow

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _showReportDialog = MutableStateFlow(false)
    val showReportDialog: StateFlow<Boolean> = _showReportDialog.asStateFlow()

    private val _showResetConfirmDialog = MutableStateFlow(false)
    val showResetConfirmDialog: StateFlow<Boolean> = _showResetConfirmDialog.asStateFlow()

    fun dismissMessage() {
        _actionMessage.value = null
    }

    fun setShowReportDialog(show: Boolean) {
        _showReportDialog.value = show
    }

    fun setShowResetConfirmDialog(show: Boolean) {
        _showResetConfirmDialog.value = show
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.refreshStatus()
            repository.fetchJournal()
            repository.refreshCIEStatus()
            _isLoading.value = false
        }
    }

    fun createCheckpoint() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.createCheckpoint("MANUAL")
            if (result.isSuccess) {
                _actionMessage.value = "📸 Snapshot created: ${result.getOrNull()?.checkpointId}"
            } else {
                _actionMessage.value = "Failed to create checkpoint snapshot"
            }
            _isLoading.value = false
        }
    }

    fun simulateBlackout() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.simulateBlackout("Simulated Primary Storage Outage")
            if (result.isSuccess) {
                _actionMessage.value = "🔴 BLACKOUT MODE ACTIVATED — Recovery Mode Active"
            } else {
                _actionMessage.value = "Failed to trigger blackout simulation"
            }
            _isLoading.value = false
        }
    }

    fun restoreSystem() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.restoreSystem()
            if (result.isSuccess) {
                val report = result.getOrNull()
                _actionMessage.value = "🟢 System Restored! Replayed ${report?.successfullyRecovered ?: 0} transactions."
                _showReportDialog.value = true
            } else {
                _actionMessage.value = "System recovery failed"
            }
            _isLoading.value = false
        }
    }

    fun resetDemo() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.resetDemo()
            if (result.isSuccess) {
                _actionMessage.value = "♻ Demo Reset to clean baseline (Rice=50, Atta=30, Milk=20)"
            } else {
                _actionMessage.value = "Failed to reset demo"
            }
            _isLoading.value = false
        }
    }

    fun simulateCIEIncident(productName: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.simulateCIEIncident(productName)
            if (result.isSuccess) {
                val res = result.getOrNull()
                _actionMessage.value = "🚨 CIE Anomaly Detected: Returns clustered across 8 partner stores!"
            } else {
                _actionMessage.value = "Failed to simulate CIE incident"
            }
            _isLoading.value = false
        }
    }

    fun resetCIEIncident() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.resetCIEIncident()
            if (result.isSuccess) {
                _actionMessage.value = "Demo incident reset successfully"
            } else {
                _actionMessage.value = "Failed to reset demo incident"
            }
            _showResetConfirmDialog.value = false
            _isLoading.value = false
        }
    }
}
