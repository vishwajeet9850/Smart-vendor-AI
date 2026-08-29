package com.smartvendor.ai.ai

import com.smartvendor.ai.model.Bill
import com.smartvendor.ai.model.Product
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized Cooldown Manager for OCR and YOLO detection.
 * Ensures that whenever a product is added or removed/cancelled/undone,
 * it is prevented from being repeatedly detected or re-added by both OCR and YOLO.
 */
object ScanCooldownManager {

    // 10-second cooldown after adding a product
    const val ADDED_COOLDOWN_MS = 10_000L

    // 12-second cooldown after removing or cancelling a product
    const val REMOVED_COOLDOWN_MS = 12_000L

    private val addedTimestamps = ConcurrentHashMap<String, Long>()
    private val removedTimestamps = ConcurrentHashMap<String, Long>()

    // Label mapping helper to resolve fine-tuned YOLO classes and common brands
    private val labelKeywordMap = mapOf(
        "appe_fizz" to listOf("appe_fizz", "appy", "appe", "fizz", "appy fizz", "PROD_APPE_FIZZ"),
        "haldiram_soya_stick" to listOf("haldiram_soya_stick", "haldiram", "soya", "sticks", "PROD_SOYA_STICK"),
        "hide_and_seek" to listOf("hide_and_seek", "hide", "seek", "hide & seek", "PROD_HIDE_SEEK"),
        "jim_jam" to listOf("jim_jam", "jim", "jam", "treat", "PROD_JIM_JAM"),
        "maggi" to listOf("maggi", "noodles", "2-minute", "PROD_MAGGI_2MIN"),
        "nivea_deodorant" to listOf("nivea_deodorant", "nivea", "fresh active", "PROD_NIVEA_DEO"),
        "oreo" to listOf("oreo", "vanilla cream", "cadbury", "PROD_OREO_BISCUIT"),
        "surf_excel" to listOf("surf_excel", "surf", "excel", "detergent", "PROD_SURF_EXCEL"),
        "tresemme_shampoo" to listOf("tresemme_shampoo", "tresemme", "keratin", "shampoo", "PROD_TRESEMME")
    )

    private val finePrintNoise = setOf(
        "net", "wt", "mfg", "exp", "batch", "pack", "ingredients", "made", "india",
        "mrp", "incl", "taxes", "tax", "customer", "care", "lic", "iso", "store",
        "cool", "dry", "place", "recyclable", "use", "best", "before", "date",
        "weight", "grams", "kilograms", "quantity", "address", "marketed",
        "manufactured", "ltd", "pvt", "corp", "inc", "product", "details", "contact",
        "nutrition", "nutritional", "facts", "information", "per", "serve", "serving",
        "size", "energy", "protein", "carbohydrate", "sugar", "fat", "saturated",
        "vegetarian", "veg", "green", "dot", "fssai", "license", "reg", "tm", "only"
    )

    private fun extractKeys(
        productId: String?,
        productName: String?,
        barcode: String? = null,
        label: String? = null
    ): Set<String> {
        val keys = mutableSetOf<String>()
        if (!productId.isNullOrBlank()) {
            keys.add(productId.trim().lowercase(Locale.ROOT))
        }
        if (!barcode.isNullOrBlank()) {
            keys.add(barcode.trim().lowercase(Locale.ROOT))
        }
        if (!label.isNullOrBlank()) {
            val cleanLabel = label.trim().lowercase(Locale.ROOT)
            keys.add(cleanLabel)
            labelKeywordMap[cleanLabel]?.forEach { keys.add(it.lowercase(Locale.ROOT)) }
        }
        if (!productName.isNullOrBlank()) {
            val cleanName = productName.trim().lowercase(Locale.ROOT)
            keys.add(cleanName)
            // Extract tokens
            val tokens = cleanName.split(Regex("[^a-zA-Z0-9]+"))
                .filter { it.length >= 3 && it !in finePrintNoise }
            keys.addAll(tokens)

            // Match against known label aliases
            for ((lbl, aliases) in labelKeywordMap) {
                val normalizedLbl = lbl.replace("_", " ")
                if (cleanName.contains(normalizedLbl) || aliases.any { cleanName.contains(it) }) {
                    keys.add(lbl)
                    keys.addAll(aliases.map { it.lowercase(Locale.ROOT) })
                }
            }
        }
        return keys
    }

    /**
     * Mark a product as ADDED to bill.
     * Prevents OCR and YOLO from detecting/adding it again within ADDED_COOLDOWN_MS.
     */
    fun markAdded(product: Product) {
        markAdded(product.id, product.name, product.barcode)
    }

    fun markAdded(productId: String?, productName: String?, barcode: String? = null, label: String? = null) {
        val now = System.currentTimeMillis()
        val keys = extractKeys(productId, productName, barcode, label)
        for (k in keys) {
            addedTimestamps[k] = now
            removedTimestamps.remove(k)
        }
    }

    /**
     * Mark a product as REMOVED, CANCELLED, or UNDONE.
     * Prevents OCR and YOLO from detecting/adding it again within REMOVED_COOLDOWN_MS.
     */
    fun markRemoved(product: Product) {
        markRemoved(product.id, product.name, product.barcode)
    }

    fun markRemoved(productId: String?, productName: String?, barcode: String? = null, label: String? = null) {
        val now = System.currentTimeMillis()
        val keys = extractKeys(productId, productName, barcode, label)
        for (k in keys) {
            removedTimestamps[k] = now
            addedTimestamps.remove(k)
        }
    }

    /**
     * Check whether a specific Product instance is currently in cooldown.
     */
    fun isProductInCooldown(product: Product): Boolean {
        return isCooldownActive(product.id, product.name, product.barcode)
    }

    /**
     * Check whether a YOLO label is currently in cooldown.
     */
    fun isLabelInCooldown(label: String): Boolean {
        return isCooldownActive(null, null, null, label)
    }

    /**
     * Check whether raw or parsed OCR scanned text references a product in cooldown.
     */
    fun isTextInCooldown(scannedText: String): Boolean {
        if (scannedText.isBlank()) return false
        val textLower = scannedText.lowercase(Locale.ROOT)
        val now = System.currentTimeMillis()

        // 1. Check active removal cooldowns
        for ((key, timestamp) in removedTimestamps) {
            if (key.length >= 3 && (now - timestamp < REMOVED_COOLDOWN_MS)) {
                if (textLower.contains(key)) return true
            }
        }

        // 2. Check active addition cooldowns
        for ((key, timestamp) in addedTimestamps) {
            if (key.length >= 3 && (now - timestamp < ADDED_COOLDOWN_MS)) {
                if (textLower.contains(key)) return true
            }
        }

        return false
    }

    /**
     * Checks if any key associated with the given item is within cooldown.
     */
    fun isCooldownActive(
        productId: String?,
        productName: String?,
        barcode: String? = null,
        label: String? = null
    ): Boolean {
        val now = System.currentTimeMillis()
        val keys = extractKeys(productId, productName, barcode, label)
        for (k in keys) {
            val remTime = removedTimestamps[k] ?: 0L
            if (now - remTime < REMOVED_COOLDOWN_MS) {
                return true
            }
            val addTime = addedTimestamps[k] ?: 0L
            if (now - addTime < ADDED_COOLDOWN_MS) {
                return true
            }
        }
        return false
    }

    /**
     * Refreshes cooldowns for all items present in the given bill.
     * Prevents items already in the bill from being re-detected immediately when resuming the scanner.
     */
    fun refreshCooldownsForBill(bill: Bill) {
        val now = System.currentTimeMillis()
        for (item in bill.items) {
            val keys = extractKeys(item.productId, item.name)
            for (k in keys) {
                addedTimestamps[k] = now
                removedTimestamps.remove(k)
            }
        }
    }

    /**
     * Resets all active cooldowns.
     */
    fun clear() {
        addedTimestamps.clear()
        removedTimestamps.clear()
    }
}
