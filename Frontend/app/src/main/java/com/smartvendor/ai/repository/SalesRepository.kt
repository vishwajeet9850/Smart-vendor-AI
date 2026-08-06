package com.smartvendor.ai.repository

import com.smartvendor.ai.model.Bill
import kotlinx.coroutines.flow.Flow

interface SalesRepository {
    suspend fun getOpenBillingSession(): Result<Bill?>
    suspend fun createNewBillingSession(cashierId: String, storeId: String): Result<Bill>
    suspend fun getBillById(billId: String): Result<Bill?>
    suspend fun saveBill(bill: Bill): Result<Unit>
    fun getSalesHistoryStream(): Flow<List<Bill>>
}
