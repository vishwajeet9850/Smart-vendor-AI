package com.smartvendor.ai.utils

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.widget.Toast
import com.smartvendor.ai.model.Bill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmsUtils {

    private const val PREFS_NAME = "smartvendor_sms_prefs"
    private const val KEY_SMS_COUNT = "sms_count_today"
    private const val KEY_LAST_DATE = "sms_last_date"
    const val DAILY_SMS_LIMIT = 100

    fun getDailySmsCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString(KEY_LAST_DATE, "")

        if (lastDate != todayStr) {
            prefs.edit().putString(KEY_LAST_DATE, todayStr).putInt(KEY_SMS_COUNT, 0).apply()
            return 0
        }

        return prefs.getInt(KEY_SMS_COUNT, 0)
    }

    private fun incrementDailySmsCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = getDailySmsCount(context)
        prefs.edit().putInt(KEY_SMS_COUNT, currentCount + 1).apply()
    }

    fun formatBillSmsText(bill: Bill): String {
        val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        val dateStr = try {
            dateFormat.format(Date(bill.timestamp))
        } catch (_: Exception) {
            dateFormat.format(Date())
        }

        val itemsText = bill.items.joinToString("\n") { item ->
            "• ${item.name} x${item.quantity} = ₹${"%.2f".format(item.lineTotal)}"
        }

        return """
SMARTVENDOR RECEIPT
Bill #${bill.billId.takeLast(6)} ($dateStr)
---
$itemsText
---
Subtotal: ₹${"%.2f".format(bill.subtotal)}
GST: ₹${"%.2f".format(bill.gst)}
TOTAL: ₹${"%.2f".format(bill.grandTotal)} (${bill.paymentMethod})
Thank you for shopping with us!
        """.trimIndent()
    }

    fun sendSilentSmsReceipt(context: Context, phoneNumber: String, bill: Bill): Boolean {
        val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
        val targetPhone = if (cleanPhone.length == 10) "+91$cleanPhone" else cleanPhone

        if (cleanPhone.length < 10) {
            Toast.makeText(context, "Please enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
            return false
        }

        val currentSmsCount = getDailySmsCount(context)
        if (currentSmsCount >= DAILY_SMS_LIMIT) {
            Toast.makeText(
                context,
                "⚠️ Daily 100 free SMS limit reached for today! Switching to WhatsApp...",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val smsText = formatBillSmsText(bill)

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val messageParts = smsManager.divideMessage(smsText)
            if (messageParts.size > 1) {
                smsManager.sendMultipartTextMessage(targetPhone, null, messageParts, null, null)
            } else {
                smsManager.sendTextMessage(targetPhone, null, smsText, null, null)
            }

            incrementDailySmsCount(context)
            val newCount = currentSmsCount + 1
            Toast.makeText(context, "🚀 Digital Receipt sent via SMS ($newCount/$DAILY_SMS_LIMIT today)", Toast.LENGTH_LONG).show()
            true
        } catch (ex: Exception) {
            Toast.makeText(context, "SMS Send Failed: ${ex.message}", Toast.LENGTH_LONG).show()
            false
        }
    }
}
