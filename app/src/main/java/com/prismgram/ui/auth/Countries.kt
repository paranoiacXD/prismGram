package com.prismgram.ui.auth

import android.content.Context
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

data class CountryInfo(
    val iso: String,
    val dialCode: String,
    val name: String,
    val flag: String,
)

// country list + phone number parsing. flags are just emoji so no image assets needed
object Countries {

    private val phoneUtil = PhoneNumberUtil.getInstance()

    val all: List<CountryInfo> by lazy { buildCountries() }

    fun byIso(iso: String): CountryInfo? =
        all.firstOrNull { it.iso.equals(iso, ignoreCase = true) }

    // sim first, then network, then locale
    fun defaultFor(context: Context): CountryInfo {
        deviceCountry(context)?.let { iso ->
            byIso(iso)?.let { return it }
        }
        return byIso(Locale.getDefault().country.ifBlank { "US" })
            ?: all.firstOrNull { it.iso == "US" }
            ?: all.first()
    }

    // guess the country from the digits the user typed, falls back to sim/locale for local numbers
    fun guess(digits: String, fallback: CountryInfo?): CountryInfo? {
        if (digits.isEmpty()) return null
        detectInternational(digits)?.let { return it }
        return fallback
    }

    // only returns something if the digits are a full valid international number
    fun detectInternational(digits: String): CountryInfo? {
        if (digits.length < 4) return null
        val number = runCatching { phoneUtil.parse("+$digits", null) }.getOrNull() ?: return null
        if (!phoneUtil.isPossibleNumber(number)) return null
        val region = phoneUtil.getRegionCodeForNumber(number) ?: return null
        return byIso(region)
    }

    // makes +XXXXXXXXXX out of whatever was typed
    fun resolveE164(digits: String, selected: CountryInfo?): String? {
        val clean = digits.filter { it.isDigit() }
        if (clean.isEmpty()) return null

        detectInternational(clean)?.let {
            runCatching {
                return phoneUtil.format(
                    phoneUtil.parse("+$clean", null),
                    PhoneNumberUtil.PhoneNumberFormat.E164,
                )
            }
        }

        selected?.let { country ->
            runCatching {
                val number = phoneUtil.parse(clean, country.iso)
                return phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
            }
        }

        return selected?.let { it.dialCode + clean } ?: "+$clean"
    }

    private fun deviceCountry(context: Context): String? = runCatching {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val sim = telephony?.simCountryIso
        if (!sim.isNullOrBlank()) return@runCatching sim
        val network = telephony?.networkCountryIso
        if (!network.isNullOrBlank()) return@runCatching network
        null
    }.getOrNull()

    private fun buildCountries(): List<CountryInfo> {
        val result = ArrayList<CountryInfo>()
        for (region in phoneUtil.supportedRegions) {
            if (region.length != 2) continue
            val callingCode = phoneUtil.getCountryCodeForRegion(region)
            if (callingCode <= 0) continue
            val name = Locale("", region).displayCountry
            if (name.isBlank()) continue
            result.add(
                CountryInfo(
                    iso = region,
                    dialCode = "+$callingCode",
                    name = name,
                    flag = region.toFlagEmoji(),
                ),
            )
        }
        return result.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    private fun String.toFlagEmoji(): String {
        if (length != 2) return ""
        val regionalIndicatorBase = 0x1F1E6
        val asciiA = 'A'.code
        return buildString(4) {
            appendCodePoint(regionalIndicatorBase + (this@toFlagEmoji[0].uppercaseChar().code - asciiA))
            appendCodePoint(regionalIndicatorBase + (this@toFlagEmoji[1].uppercaseChar().code - asciiA))
        }
    }
}
