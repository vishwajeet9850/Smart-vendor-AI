package com.smartvendor.ai.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URLEncoder

object QrCodeUtils {

    /**
     * Generates a standard NPCI UPI Payment URI string:
     * upi://pay?pa=store@upi&pn=StoreName&am=150.00&tn=Bill_123&cu=INR
     */
    fun generateUpiPayUri(
        upiId: String,
        storeName: String,
        amount: Double,
        billId: String
    ): String {
        val cleanUpi = upiId.trim().ifBlank { "smartvendor@upi" }
        val cleanStore = storeName.trim().ifBlank { "SmartVendor Store" }
        val formattedAmount = "%.2f".format(amount)
        val encodedStore = try {
            URLEncoder.encode(cleanStore, "UTF-8")
        } catch (e: Exception) {
            "SmartVendor"
        }
        return "upi://pay?pa=$cleanUpi&pn=$encodedStore&am=$formattedAmount&tn=Bill+$billId&cu=INR"
    }

    /**
     * Generates a square Bitmap of a QR Code for any given string content (e.g. UPI URI).
     */
    fun generateQrCodeBitmap(
        content: String,
        sizePx: Int = 512,
        darkColor: Int = Color.BLACK,
        lightColor: Int = Color.WHITE
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 1
            )
            val writer = QRCodeWriter()
            val matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) darkColor else lightColor)
                }
            }
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
