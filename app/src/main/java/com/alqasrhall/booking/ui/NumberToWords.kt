@file:Suppress("MagicNumber", "ComplexMethod", "NestedBlockDepth", "LongMethod")

package com.alqasrhall.booking.ui

object NumberToWords {

    private val units = arrayOf("", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة")
    private val teens = arrayOf("عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر")
    private val tens = arrayOf("", "عشرة", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون")
    private val hundreds = arrayOf("", "مائة", "مائتان", "ثلاثمائة", "أربعمائة", "خمسمائة", "ستمائة", "سبعمائة", "ثمانمائة", "تسعمائة")

    fun convertToArabic(number: Long, currency: String): String {
        if (number == 0L) return "صفر"
        
        val parts = mutableListOf<String>()
        val temp = number

        val billions = (temp / 1_000_000_000) % 1000
        val millions = (temp / 1_000_000) % 1000
        val thousands = (temp / 1000) % 1000
        val ones = temp % 1000

        if (billions > 0) {
            parts.add(convertGroupToArabic(billions.toInt(), "مليار", "ملياران", "مليارات"))
        }
        if (millions > 0) {
            parts.add(convertGroupToArabic(millions.toInt(), "مليون", "مليونان", "ملايين"))
        }
        if (thousands > 0) {
            parts.add(convertGroupToArabic(thousands.toInt(), "ألف", "ألفان", "آلاف"))
        }
        if (ones > 0) {
            parts.add(convertThreeDigitsToArabic(ones.toInt()))
        }

        val result = parts.filter { it.isNotEmpty() }.joinToString(" و ")
        
        val currencySuffix = when (currency) {
            "Saudi Riyal" -> "ريال سعودي"
            "USD" -> "دولار أمريكي"
            else -> "ريال يمني"
        }
        
        return "$result $currencySuffix"
    }

    private fun convertGroupToArabic(value: Int, singular: String, dual: String, plural: String): String {
        return when {
            value == 1 -> singular
            value == 2 -> dual
            value in 3..10 -> "${units[value]} $plural"
            else -> "${convertThreeDigitsToArabic(value)} $singular"
        }
    }

    private fun convertThreeDigitsToArabic(value: Int): String {
        if (value == 0) return ""
        val parts = mutableListOf<String>()

        val h = value / 100
        val rem = value % 100

        if (h > 0) {
            parts.add(hundreds[h])
        }

        if (rem > 0) {
            if (rem in 1..10) {
                parts.add(units[rem])
            } else if (rem in 11..19) {
                parts.add(teens[rem - 10])
            } else {
                val u = rem % 10
                val t = rem / 10
                val unitWord = if (u > 0) units[u] else ""
                val tenWord = tens[t]
                if (unitWord.isNotEmpty()) {
                    parts.add("$unitWord و$tenWord")
                } else {
                    parts.add(tenWord)
                }
            }
        }

        return parts.joinToString(" و ")
    }

    // English converter for USD
    private val englishUnits = arrayOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
    private val englishTens = arrayOf("", "Ten", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

    fun convertToEnglish(number: Long, currency: String): String {
        if (number == 0L) return "Zero"
        
        val parts = mutableListOf<String>()
        val temp = number

        val billions = (temp / 1_000_000_000) % 1000
        val millions = (temp / 1_000_000) % 1000
        val thousands = (temp / 1000) % 1000
        val ones = temp % 1000

        if (billions > 0) {
            parts.add("${convertThreeDigitsToEnglish(billions.toInt())} Billion")
        }
        if (millions > 0) {
            parts.add("${convertThreeDigitsToEnglish(millions.toInt())} Million")
        }
        if (thousands > 0) {
            parts.add("${convertThreeDigitsToEnglish(thousands.toInt())} Thousand")
        }
        if (ones > 0) {
            parts.add(convertThreeDigitsToEnglish(ones.toInt()))
        }

        val result = parts.joinToString(", ")
        val currencySuffix = when (currency) {
            "Saudi Riyal" -> "Saudi Riyals"
            "USD" -> "US Dollars"
            else -> "Yemeni Rials"
        }
        return "$result $currencySuffix"
    }

    private fun convertThreeDigitsToEnglish(value: Int): String {
        val parts = mutableListOf<String>()
        val h = value / 100
        val rem = value % 100

        if (h > 0) {
            parts.add("${englishUnits[h]} Hundred")
        }

        if (rem > 0) {
            if (rem < 20) {
                parts.add(englishUnits[rem])
            } else {
                val u = rem % 10
                val t = rem / 10
                if (u > 0) {
                    parts.add("${englishTens[t]}-${englishUnits[u]}")
                } else {
                    parts.add(englishTens[t])
                }
            }
        }

        return parts.joinToString(" and ")
    }
}
