package com.smartvendor.ai.repository

import com.smartvendor.ai.model.CIEAlertModel
import com.smartvendor.ai.model.CIEStatusResponse
import com.smartvendor.ai.model.JournalTransaction
import com.smartvendor.ai.model.RecoveryReport
import com.smartvendor.ai.model.SystemCheckpoint
import com.smartvendor.ai.model.SystemStatus
import com.smartvendor.ai.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface ResilienceRepository {
    val systemStatusFlow: StateFlow<SystemStatus>
    val journalFlow: StateFlow<List<JournalTransaction>>
    val latestReportFlow: StateFlow<RecoveryReport?>
    val cieAlertFlow: StateFlow<CIEAlertModel?>
    val hasActiveCIEIncidentFlow: StateFlow<Boolean>

    suspend fun refreshStatus(): Result<SystemStatus>
    suspend fun createCheckpoint(type: String = "MANUAL"): Result<SystemCheckpoint>
    suspend fun simulateBlackout(reason: String? = null): Result<SystemStatus>
    suspend fun restoreSystem(): Result<RecoveryReport>
    suspend fun resetDemo(): Result<SystemStatus>
    suspend fun fetchJournal(limit: Int = 50, offset: Int = 0): Result<List<JournalTransaction>>

    suspend fun refreshCIEStatus(): Result<CIEStatusResponse>
    suspend fun simulateCIEIncident(productName: String? = null): Result<CIEStatusResponse>
    suspend fun resetCIEIncident(): Result<CIEStatusResponse>
}

class ResilienceRepositoryImpl(
    private val localStoreManager: LocalStoreManager = LocalStoreManager
) : ResilienceRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _systemStatusFlow = MutableStateFlow(SystemStatus())
    override val systemStatusFlow: StateFlow<SystemStatus> = _systemStatusFlow.asStateFlow()

    private val _journalFlow = MutableStateFlow<List<JournalTransaction>>(emptyList())
    override val journalFlow: StateFlow<List<JournalTransaction>> = _journalFlow.asStateFlow()

    private val _latestReportFlow = MutableStateFlow<RecoveryReport?>(null)
    override val latestReportFlow: StateFlow<RecoveryReport?> = _latestReportFlow.asStateFlow()

    private val _cieAlertFlow = MutableStateFlow<CIEAlertModel?>(null)
    override val cieAlertFlow: StateFlow<CIEAlertModel?> = _cieAlertFlow.asStateFlow()

    private val _hasActiveCIEIncidentFlow = MutableStateFlow(false)
    override val hasActiveCIEIncidentFlow: StateFlow<Boolean> = _hasActiveCIEIncidentFlow.asStateFlow()

    init {
        scope.launch {
            refreshStatus()
            fetchJournal()
            refreshCIEStatus()
        }
    }

    override suspend fun refreshStatus(): Result<SystemStatus> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.apiService.getResilienceStatus()
            if (response.isSuccessful && response.body() != null) {
                val status = response.body()!!
                _systemStatusFlow.value = status
                if (status.latestReport != null) {
                    _latestReportFlow.value = status.latestReport
                }
                localStoreManager.setBlackoutActive(status.isBlackoutActive)
                Result.success(status)
            } else {
                // Offline fallback status from LocalStoreManager
                val isBlackout = localStoreManager.isBlackoutActive()
                val fallbackStatus = SystemStatus(
                    systemStatus = if (isBlackout) "BLACKOUT_ACTIVE" else "HEALTHY",
                    isBlackoutActive = isBlackout,
                    primaryDatabaseStatus = if (isBlackout) "CORRUPTED_UNAVAILABLE" else "ONLINE",
                    totalJournaledTransactions = localStoreManager.getLocalJournal().size
                )
                _systemStatusFlow.value = fallbackStatus
                Result.success(fallbackStatus)
            }
        } catch (e: Exception) {
            val isBlackout = localStoreManager.isBlackoutActive()
            val fallbackStatus = SystemStatus(
                systemStatus = if (isBlackout) "BLACKOUT_ACTIVE" else "HEALTHY",
                isBlackoutActive = isBlackout,
                primaryDatabaseStatus = if (isBlackout) "CORRUPTED_UNAVAILABLE" else "ONLINE",
                totalJournaledTransactions = localStoreManager.getLocalJournal().size
            )
            _systemStatusFlow.value = fallbackStatus
            Result.success(fallbackStatus)
        }
    }

    override suspend fun createCheckpoint(type: String): Result<SystemCheckpoint> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.apiService.createCheckpoint(type)
            if (response.isSuccessful && response.body() != null) {
                refreshStatus()
                Result.success(response.body()!!)
            } else {
                val localChk = localStoreManager.createLocalCheckpoint(type)
                refreshStatus()
                Result.success(localChk)
            }
        } catch (e: Exception) {
            val localChk = localStoreManager.createLocalCheckpoint(type)
            refreshStatus()
            Result.success(localChk)
        }
    }

    override suspend fun simulateBlackout(reason: String?): Result<SystemStatus> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.apiService.simulateBlackout(reason)
            if (response.isSuccessful && response.body() != null) {
                val status = response.body()!!
                _systemStatusFlow.value = status
                localStoreManager.setBlackoutActive(true)
                fetchJournal()
                Result.success(status)
            } else {
                localStoreManager.setBlackoutActive(true)
                val status = SystemStatus(
                    systemStatus = "BLACKOUT_ACTIVE",
                    isBlackoutActive = true,
                    simulatedFailureReason = reason ?: "Primary Database Simulated Outage",
                    primaryDatabaseStatus = "CORRUPTED_UNAVAILABLE"
                )
                _systemStatusFlow.value = status
                Result.success(status)
            }
        } catch (e: Exception) {
            localStoreManager.setBlackoutActive(true)
            val status = SystemStatus(
                systemStatus = "BLACKOUT_ACTIVE",
                isBlackoutActive = true,
                simulatedFailureReason = reason ?: "Primary Database Simulated Outage",
                primaryDatabaseStatus = "CORRUPTED_UNAVAILABLE"
            )
            _systemStatusFlow.value = status
            Result.success(status)
        }
    }

    override suspend fun restoreSystem(): Result<RecoveryReport> = withContext(Dispatchers.IO) {
        localStoreManager.setBlackoutActive(false)
        val localReport = localStoreManager.restoreFromLocalCheckpoint()
        localStoreManager.syncWithServer()

        // Push recovered journal entries to backend DB so Live Inspector updates
        val recoveredJournal = localStoreManager.getLocalJournal()
        try {
            val txList = recoveredJournal.map { tx ->
                mapOf(
                    "id" to tx.id,
                    "transactionId" to tx.transactionId,
                    "type" to tx.type,
                    "billId" to (tx.billId ?: ""),
                    "productId" to (tx.productId ?: ""),
                    "productName" to tx.productName,
                    "quantity" to tx.quantity,
                    "unitPrice" to tx.unitPrice,
                    "totalAmount" to tx.totalAmount,
                    "previousStock" to tx.previousStock,
                    "newStock" to tx.newStock,
                    "returnCondition" to tx.returnCondition,
                    "status" to "RECOVERED"
                )
            }
            ApiClient.apiService.syncJournal(mapOf("transactions" to txList))
        } catch (_: Exception) {}

        try {
            val response = ApiClient.apiService.restoreSystem()
            if (response.isSuccessful && response.body() != null) {
                var report = response.body()!!
                if (report.transactionsDiscovered == 0 && localReport.transactionsDiscovered > 0) {
                    report = report.copy(
                        transactionsDiscovered = localReport.transactionsDiscovered,
                        successfullyRecovered = localReport.successfullyRecovered,
                        lastCheckpointId = report.lastCheckpointId ?: localReport.lastCheckpointId
                    )
                }
                _latestReportFlow.value = report
                _journalFlow.value = localStoreManager.getLocalJournal()
                refreshStatus()
                Result.success(report)
            } else {
                _latestReportFlow.value = localReport
                _journalFlow.value = localStoreManager.getLocalJournal()
                refreshStatus()
                Result.success(localReport)
            }
        } catch (e: Exception) {
            _latestReportFlow.value = localReport
            _journalFlow.value = localStoreManager.getLocalJournal()
            refreshStatus()
            Result.success(localReport)
        }
    }


    override suspend fun resetDemo(): Result<SystemStatus> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.apiService.resetDemo()
            if (response.isSuccessful && response.body() != null) {
                val status = response.body()!!
                _systemStatusFlow.value = status
                _latestReportFlow.value = null
                localStoreManager.resetLocalDemo()
                refreshStatus()
                fetchJournal()
                Result.success(status)
            } else {
                localStoreManager.resetLocalDemo()
                refreshStatus()
                Result.success(_systemStatusFlow.value)
            }
        } catch (e: Exception) {
            localStoreManager.resetLocalDemo()
            refreshStatus()
            Result.success(_systemStatusFlow.value)
        }
    }

    override suspend fun fetchJournal(limit: Int, offset: Int): Result<List<JournalTransaction>> = withContext(Dispatchers.IO) {
        val localList = localStoreManager.getLocalJournal()
        try {
            val response = ApiClient.apiService.getTransactionJournal(limit, offset)
            if (response.isSuccessful && response.body() != null && response.body()!!.isNotEmpty()) {
                val list = response.body()!!
                _journalFlow.value = list
                Result.success(list)
            } else {
                _journalFlow.value = localList
                Result.success(localList)
            }
        } catch (e: Exception) {
            _journalFlow.value = localList
            Result.success(localList)
        }
    }

    override suspend fun refreshCIEStatus(): Result<CIEStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.apiService.getCIEStatus()
            if (response.isSuccessful && response.body() != null) {
                val res = response.body()!!
                _hasActiveCIEIncidentFlow.value = res.hasActiveIncident
                _cieAlertFlow.value = res.activeAlert
                Result.success(res)
            } else {
                Result.success(CIEStatusResponse(hasActiveIncident = _hasActiveCIEIncidentFlow.value, activeAlert = _cieAlertFlow.value))
            }
        } catch (e: Exception) {
            Result.success(CIEStatusResponse(hasActiveIncident = _hasActiveCIEIncidentFlow.value, activeAlert = _cieAlertFlow.value))
        }
    }

    override suspend fun simulateCIEIncident(productName: String?): Result<CIEStatusResponse> = withContext(Dispatchers.IO) {
        val targetProduct = productName ?: (localStoreManager.productsFlow.value.firstOrNull()?.name ?: "Soya Sticks")
        val targetPrice = localStoreManager.productsFlow.value.firstOrNull { it.name.contains(targetProduct, ignoreCase = true) }?.price ?: 20.0
        val targetProductId = localStoreManager.productsFlow.value.firstOrNull { it.name.contains(targetProduct, ignoreCase = true) }?.id ?: ""

        // Inject 3 convincing return bills directly into the user's bills and journal
        localStoreManager.injectCIEDemoReturnBills(targetProduct, targetPrice, targetProductId)

        try {
            val response = ApiClient.apiService.simulateCIEIncident(targetProduct)
            if (response.isSuccessful && response.body() != null) {
                val res = response.body()!!
                _hasActiveCIEIncidentFlow.value = true
                _cieAlertFlow.value = res.alert ?: res.activeAlert
                refreshStatus()
                fetchJournal()
                return@withContext Result.success(res)
            }
        } catch (e: Exception) {
            // Fall through to resilient on-device fallback
        }

        // Resilient on-device fallback alert
        val fallbackAlert = CIEAlertModel(
            id = java.util.UUID.randomUUID().toString(),
            incidentId = "cross_vendor_return_demo",
            productName = targetProduct,
            affectedVendorsCount = 8,
            totalReturnsCount = 8,
            timeWindowMinutes = 25,
            alertTitle = "Unusual cross-vendor return pattern detected",
            alertMessage = "🚨 CIE ALERT\nUnusual cross-vendor return pattern detected.\n\nProduct: $targetProduct\nAffected vendors: 8\nReturns: 8\nTime window: 25 minutes\n\nPossible network-wide product issue.\nVerification required.",
            status = "ACTIVE"
        )
        _hasActiveCIEIncidentFlow.value = true
        _cieAlertFlow.value = fallbackAlert
        refreshStatus()
        fetchJournal()
        Result.success(CIEStatusResponse(success = true, hasActiveIncident = true, alert = fallbackAlert))
    }

    override suspend fun resetCIEIncident(): Result<CIEStatusResponse> = withContext(Dispatchers.IO) {
        // Clear all injected demo return bills and journal transactions locally
        localStoreManager.clearCIEDemoReturnBills()

        try {
            ApiClient.apiService.resetCIEIncident()
        } catch (e: Exception) {
            // Local fallback
        }
        _hasActiveCIEIncidentFlow.value = false
        _cieAlertFlow.value = null
        refreshStatus()
        fetchJournal()
        Result.success(CIEStatusResponse(success = true, hasActiveIncident = false, message = "Demo incident reset successfully"))
    }
}

