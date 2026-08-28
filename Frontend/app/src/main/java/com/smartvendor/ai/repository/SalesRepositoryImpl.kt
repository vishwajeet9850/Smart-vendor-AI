package com.smartvendor.ai.repository

import com.smartvendor.ai.model.Bill
import kotlinx.coroutines.flow.Flow

class SalesRepositoryImpl : SalesRepository {

    companion object {
        private val draftBillsCache = mutableMapOf<String, Bill>()
    }

    override suspend fun getOpenBillingSession(): Result<Bill?> {
        val openBill = draftBillsCache.values.firstOrNull { it.status == Bill.BILL_STATUS_OPEN }
            ?: LocalStoreManager.getBills().firstOrNull { it.status == Bill.BILL_STATUS_OPEN }
        return Result.success(openBill)
    }

    override suspend fun createNewBillingSession(cashierId: String, storeId: String): Result<Bill> {
        val newBillId = "BILL_${System.currentTimeMillis()}"
        val newBill = Bill(
            billId = newBillId,
            cashierId = cashierId,
            storeId = storeId,
            status = Bill.BILL_STATUS_OPEN
        )
        draftBillsCache[newBillId] = newBill
        LocalStoreManager.saveBill(newBill)
        return Result.success(newBill)
    }

    override suspend fun getBillById(billId: String): Result<Bill?> {
        draftBillsCache[billId]?.let { return Result.success(it) }
        val bill = LocalStoreManager.getBillById(billId)
        return Result.success(bill)
    }

    override suspend fun saveBill(bill: Bill): Result<Unit> {
        if (bill.status == Bill.BILL_STATUS_COMPLETED) {
            draftBillsCache.remove(bill.billId)
        } else {
            draftBillsCache[bill.billId] = bill
        }
        LocalStoreManager.saveBill(bill)
        return Result.success(Unit)
    }

    override fun getSalesHistoryStream(): Flow<List<Bill>> {
        return LocalStoreManager.billsFlow
    }
}
