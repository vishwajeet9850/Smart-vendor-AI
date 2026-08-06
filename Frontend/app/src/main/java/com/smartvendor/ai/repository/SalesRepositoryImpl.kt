package com.smartvendor.ai.repository

import com.smartvendor.ai.model.Bill
import com.smartvendor.ai.model.BillItem
import com.smartvendor.ai.network.ApiClient
import com.smartvendor.ai.network.models.BillItemRequest
import com.smartvendor.ai.network.models.BillRequest
import com.smartvendor.ai.network.models.BillResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SalesRepositoryImpl : SalesRepository {

    private val api = ApiClient.apiService

    companion object {
        private val draftBillsCache = mutableMapOf<String, Bill>()
    }

    private fun BillResponse.toDomain() = Bill(
        billId = id,
        subtotal = totalAmount - taxAmount,
        gst = taxAmount,
        grandTotal = totalAmount,
        paymentMethod = paymentMode.uppercase(),
        status = Bill.BILL_STATUS_COMPLETED,
        items = items.map { item ->
            BillItem(
                productId = item.productId ?: "",
                name = item.productName,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                lineTotal = item.totalPrice
            )
        }
    )

    override suspend fun getOpenBillingSession(): Result<Bill?> {
        val openBill = draftBillsCache.values.firstOrNull { it.status == Bill.BILL_STATUS_OPEN }
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
        return Result.success(newBill)
    }

    override suspend fun getBillById(billId: String): Result<Bill?> {
        draftBillsCache[billId]?.let { return Result.success(it) }

        return try {
            val response = api.getBill(billId)
            if (response.isSuccessful) {
                Result.success(response.body()?.toDomain())
            } else if (response.code() == 404) {
                Result.success(null)
            } else {
                Result.failure(Exception("Server error: ${response.code()}"))
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    override suspend fun saveBill(bill: Bill): Result<Unit> {
        // Update local draft cache
        draftBillsCache[bill.billId] = bill

        // Send to backend API ONLY ONCE upon checkout completion
        if (bill.status == Bill.BILL_STATUS_COMPLETED) {
            return try {
                val body = BillRequest(
                    items = bill.items.map { item ->
                        val cleanId = if (item.productId.startsWith("MANUAL_") || item.productId.isBlank()) null else item.productId
                        BillItemRequest(
                            productId = cleanId,
                            productName = item.name,
                            quantity = item.quantity,
                            unitPrice = item.unitPrice,
                            totalPrice = item.lineTotal
                        )
                    },
                    totalAmount = bill.grandTotal,
                    taxAmount = bill.gst,
                    paymentMode = bill.paymentMethod.lowercase()
                )
                val response = api.createBill(body)
                if (response.isSuccessful) {
                    draftBillsCache.remove(bill.billId)
                    Result.success(Unit)
                } else {
                    val errText = response.errorBody()?.string() ?: "Checkout failed: ${response.code()}"
                    Result.failure(Exception(errText))
                }
            } catch (ex: Exception) {
                Result.failure(ex)
            }
        }

        return Result.success(Unit)
    }

    override fun getSalesHistoryStream(): Flow<List<Bill>> = flow {
        try {
            val response = api.getBills()
            if (response.isSuccessful) {
                emit(response.body()?.map { it.toDomain() } ?: emptyList())
            } else {
                emit(emptyList())
            }
        } catch (ex: Exception) {
            emit(emptyList())
        }
    }
}
