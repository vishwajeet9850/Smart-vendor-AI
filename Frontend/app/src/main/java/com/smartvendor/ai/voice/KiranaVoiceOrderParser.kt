package com.smartvendor.ai.voice

import com.smartvendor.ai.model.Product
import com.smartvendor.ai.repository.LocalStoreManager

data class ParsedVoiceItem(
    val matchedProduct: Product?,
    val rawSpokenText: String,
    val quantity: Int = 1,
    val isOutOfStock: Boolean = false,
    val availableStock: Int = 0,
    val warningMessage: String? = null
)

object KiranaVoiceOrderParser {

    // Number word mappings for Marathi, Hindi & English
    private val numberMap = mapOf(
        // Marathi numerals & words
        "१" to 1, "एक" to 1, "एका" to 1,
        "२" to 2, "दोन" to 2, "दोन्ही" to 2,
        "३" to 3, "तीन" to 3,
        "४" to 4, "चार" to 4,
        "५" to 5, "पाच" to 5,
        "६" to 6, "सहा" to 6,
        "७" to 7, "सात" to 7,
        "८" to 8, "आठ" to 8,
        "९" to 9, "नऊ" to 9,
        "१०" to 10, "दहा" to 10,
        "अर्धा" to 1, "पाव" to 1, "दीड" to 2, "अडीच" to 3,

        // Hindi numerals & words
        "दो" to 2, "पाँच" to 5, "छह" to 6, "नौ" to 9, "दस" to 10,
        "आधा" to 1, "डेढ़" to 2, "ढाई" to 3,

        // English words
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "half" to 1, "a" to 1, "an" to 1
    )

    // Stopwords in Marathi, Hindi & English (e.g. "and", "give", "please", "packet")
    private val fillerWords = setOf(
        // Marathi
        "आणि", "व", "द्या", "द्याना", "पाहिजे", "आहे", "पाकीट", "पाकिटे", "किलो", "लिटर", "नग", "बाटली", "डबा",
        "कृपया", "घेऊन", "टाका", "जोडा", "करा",
        // Hindi
        "और", "तथा", "दो", "दीजिए", "चाहिए", "है", "पैकेट", "किलो", "लीटर", "पीस", "बोतल", "डिब्बा",
        "डालो", "जोड़ो", "करो",
        // English
        "and", "give", "me", "please", "packet", "packets", "kg", "grams", "liter", "litres", "bottle",
        "bottles", "pcs", "piece", "pieces", "add", "put", "want", "of"
    )

    // Common Devanagari / Marathi Kirana terms ↔ English product names
    private val devanagariSynonyms = mapOf(
        "मॅगी" to "maggi", "मैगी" to "maggi",
        "ओरिओ" to "oreo", "ओरियों" to "oreo",
        "पार्ले" to "parle", "पार्ले जी" to "parle g", "पारले" to "parle",
        "अमूल" to "amul", "बटर" to "butter", "अमूल बटर" to "amul butter",
        "दूध" to "milk", "दुध" to "milk",
        "साखर" to "sugar", "शक्कर" to "sugar", "चीनी" to "sugar",
        "गहू" to "wheat", "आटा" to "atta", "गव्हाचे पीठ" to "atta",
        "तांदूळ" to "rice", "चावल" to "rice", "भात" to "rice",
        "तूप" to "ghee", "घी" to "ghee",
        "तेल" to "oil", "गोडेतेल" to "oil", "सोयाबीन तेल" to "oil",
        "साबण" to "soap", "लाईफबॉय" to "lifebuoy", "डेटॉल" to "dettol",
        "सर्फ" to "surf", "सर्फ एक्सेल" to "surf excel", "पावडर" to "powder",
        "चहा" to "tea", "चाय" to "tea", "पत्ती" to "tea",
        "हळद" to "turmeric", "हल्दी" to "turmeric",
        "मीठ" to "salt", "नमक" to "salt",
        "जिम जॅम" to "jim jam", "जिमजॅम" to "jim jam",
        "हायड अँड सीक" to "hide & seek", "हाइड एंड सीक" to "hide & seek",
        "अॅपी" to "appy", "अॅपी फिझ" to "appy fizz", "एप्पी" to "appy fizz",
        "बिस्किट" to "biscuit", "बिस्कीट" to "biscuit",
        "शॅम्पू" to "shampoo",
        "कुरकुरे" to "kurkure", "वेफर्स" to "chips", "चिप्स" to "chips",
        "गुड डे" to "good day", "मोनाको" to "monaco", "क्रॅक जॅक" to "krackjack",
        "मंच" to "munch", "कॅडबरी" to "cadbury", "डेअरी मिल्क" to "dairy milk",
        "फ्रोटी" to "frooti", "माझा" to "maaza", "स्प्राइट" to "sprite", "कोक" to "coca cola"
    )

    /**
     * Parses a spoken sentence into a list of structured products with quantities & stock check.
     * Example input: "२ पाकीट मॅगी आणि १ अमूल बटर द्या"
     */
    fun parseVoiceTranscript(rawTranscript: String): List<ParsedVoiceItem> {
        val cleanText = rawTranscript.trim().lowercase()
        if (cleanText.isBlank()) return emptyList()

        val allProducts = LocalStoreManager.getProducts()
        val results = mutableListOf<ParsedVoiceItem>()

        // Split transcript on conjunctions ("आणि", "और", "and", ",", "&", "तसेच")
        val clauses = cleanText.split(Regex("""(,\s*|\s+(आणि|व|तसेच|और|तथा|and|plus|with|&)\s+)"""))

        for (clause in clauses) {
            val trimmedClause = clause.trim()
            if (trimmedClause.isBlank()) continue

            val tokens = trimmedClause.split(Regex("""\s+""")).filter { it.isNotBlank() }

            // 1. Extract Quantity (e.g. "२", "2", "दोन", "two")
            var quantity = 1
            val remainingTokens = mutableListOf<String>()

            for (token in tokens) {
                val digitVal = token.toIntOrNull()
                val wordVal = numberMap[token]

                if (digitVal != null && digitVal > 0) {
                    quantity = digitVal
                } else if (wordVal != null) {
                    quantity = wordVal
                } else {
                    remainingTokens.add(token)
                }
            }

            // 2. Remove filler words (पाकीट, द्या, please, etc.)
            val searchTokens = remainingTokens.filterNot { fillerWords.contains(it) }
            val queryRaw = searchTokens.joinToString(" ").trim()
            if (queryRaw.isBlank()) continue

            // 3. Resolve Devanagari transliterated phrases
            var translatedQuery = queryRaw
            for ((marathiTerm, englishTerm) in devanagariSynonyms) {
                if (translatedQuery.contains(marathiTerm)) {
                    translatedQuery = translatedQuery.replace(marathiTerm, englishTerm)
                }
            }

            // 4. Match against active store inventory using fuzzy search
            val matchedProduct = findBestProductMatch(translatedQuery, queryRaw, allProducts)

            if (matchedProduct != null) {
                val isOut = matchedProduct.stock <= 0
                val isLow = matchedProduct.stock in 1 until quantity
                val warning = when {
                    isOut -> "${matchedProduct.name} is OUT OF STOCK (0 left)!"
                    isLow -> "Only ${matchedProduct.stock} units left in stock for ${matchedProduct.name}."
                    else -> null
                }

                results.add(
                    ParsedVoiceItem(
                        matchedProduct = matchedProduct,
                        rawSpokenText = queryRaw,
                        quantity = quantity,
                        isOutOfStock = isOut,
                        availableStock = matchedProduct.stock,
                        warningMessage = warning
                    )
                )
            } else {
                // Not found in active store inventory, check master catalog
                val catalogMatch = LocalStoreManager.searchMasterCatalog(translatedQuery, limit = 1).firstOrNull()
                if (catalogMatch != null) {
                    val virtualProd = Product(
                        id = catalogMatch.id,
                        name = catalogMatch.name,
                        category = catalogMatch.category,
                        price = catalogMatch.suggestedPrice,
                        stock = 0,
                        barcode = catalogMatch.barcode ?: ""
                    )
                    results.add(
                        ParsedVoiceItem(
                            matchedProduct = virtualProd,
                            rawSpokenText = queryRaw,
                            quantity = quantity,
                            isOutOfStock = true,
                            availableStock = 0,
                            warningMessage = "${catalogMatch.name} is not stocked in your store."
                        )
                    )
                } else {
                    results.add(
                        ParsedVoiceItem(
                            matchedProduct = null,
                            rawSpokenText = queryRaw,
                            quantity = quantity,
                            isOutOfStock = true,
                            availableStock = 0,
                            warningMessage = "Product '$queryRaw' not found in store."
                        )
                    )
                }
            }
        }

        return results
    }

    private fun findBestProductMatch(translatedQuery: String, rawQuery: String, products: List<Product>): Product? {
        val cleanTrans = translatedQuery.lowercase().trim()
        val cleanRaw = rawQuery.lowercase().trim()

        // 1. Exact match on translated or raw name
        products.firstOrNull { it.name.equals(cleanTrans, ignoreCase = true) || it.name.equals(cleanRaw, ignoreCase = true) }
            ?.let { return it }

        // 2. Starts with / Contains match
        products.firstOrNull { it.name.contains(cleanTrans, ignoreCase = true) || cleanTrans.contains(it.name, ignoreCase = true) }
            ?.let { return it }

        // 3. Multi-token match (e.g. "amul" & "butter")
        val queryTokens = cleanTrans.split(" ").filter { it.length >= 2 }
        if (queryTokens.isNotEmpty()) {
            products.maxByOrNull { prod ->
                val prodNameLower = prod.name.lowercase()
                queryTokens.count { t -> prodNameLower.contains(t) }
            }?.let { candidate ->
                val matchCount = queryTokens.count { t -> candidate.name.lowercase().contains(t) }
                if (matchCount >= 1) return candidate
            }
        }

        return null
    }
}
