package com.smartvendor.ai.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.smartvendor.ai.model.Bill
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WhatsAppUtils {

    fun formatBillText(bill: Bill): String {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateStr = try {
            dateFormat.format(Date(bill.timestamp))
        } catch (_: Exception) {
            dateFormat.format(Date())
        }

        val itemsText = bill.items.mapIndexed { idx, item ->
            "${idx + 1}. *${item.name}* x ${item.quantity} = ₹${"%.2f".format(item.lineTotal)}"
        }.joinToString("\n")

        return """
🧾 *SMARTVENDOR STORE RECEIPT*
--------------------------------
*Bill ID:* ${bill.billId.takeLast(8)}
*Date:* $dateStr

*Items Purchased:*
$itemsText

--------------------------------
*Subtotal:* ₹${"%.2f".format(bill.subtotal)}
*GST:* ₹${"%.2f".format(bill.gst)}
*Grand Total: ₹${"%.2f".format(bill.grandTotal)}*
*Payment Mode:* ${bill.paymentMethod}

Thank you for shopping with us! 🙏
        """.trimIndent()
    }

    fun sendWhatsAppBill(context: Context, phoneNumber: String, bill: Bill) {
        val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
        val targetPhone = if (cleanPhone.length == 10) "91$cleanPhone" else cleanPhone

        val rawText = formatBillText(bill)
        val encodedText = try {
            URLEncoder.encode(rawText, "UTF-8")
        } catch (_: Exception) {
            rawText
        }

        val uriString = if (targetPhone.isNotBlank()) {
            "https://api.whatsapp.com/send?phone=$targetPhone&text=$encodedText"
        } else {
            "https://api.whatsapp.com/send?text=$encodedText"
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))

        try {
            context.startActivity(intent)
        } catch (ex: Exception) {
            Toast.makeText(
                context,
                "WhatsApp is not installed on this device",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
