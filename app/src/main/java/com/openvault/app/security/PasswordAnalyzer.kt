package com.openvault.app.security

import java.security.SecureRandom
import kotlin.math.log2
import kotlin.math.roundToInt

enum class PasswordStrengthLevel(val labelCs: String) {
    WEAK("Slabé"),
    MEDIUM("Střední"),
    STRONG("Silné"),
    CRYPTOGRAPHIC("Kryptograficky silné")
}

data class PasswordStrengthReport(
    val score: Int, // 0..100
    val entropyBits: Double,
    val level: PasswordStrengthLevel,
    val warnings: List<String>
)

data class PasswordGeneratorConfig(
    val length: Int = 20,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val excludeAmbiguous: Boolean = false,
    val passphraseMode: Boolean = false,
    val passphraseWordCount: Int = 5
)

object PasswordAnalyzer {
    private val secureRandom = SecureRandom()

    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val UPPER_AMBIGUOUS = "IO"
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val LOWER_AMBIGUOUS = "l"
    private const val DIGITS = "23456789"
    private const val DIGITS_AMBIGUOUS = "01"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?"

    private val dictionaryWords = listOf(
        "krystal", "trezor", "kompas", "horizont", "galaxie", "sifra", "kremen", "bastion",
        "impuls", "foton", "vektor", "spektrum", "monolit", "orbital", "zenit", "kaskada",
        "plazma", "majak", "aurum", "kobalt", "pulsar", "vortex", "hranice", "pevnina",
        "safir", "titan", "smaragd", "komet", "reaktor", "signatura", "protokol", "matrice"
    )

    fun analyzePassword(password: String): PasswordStrengthReport {
        if (password.isEmpty()) {
            return PasswordStrengthReport(
                score = 0,
                entropyBits = 0.0,
                level = PasswordStrengthLevel.WEAK,
                warnings = listOf("Heslo je prázdné")
            )
        }

        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        var poolSize = 0
        if (hasLower) poolSize += 26
        if (hasUpper) poolSize += 26
        if (hasDigit) poolSize += 10
        if (hasSymbol) poolSize += 32
        if (poolSize == 0) poolSize = 26

        val uniqueCharsRatio = password.toSet().size.toDouble() / password.length.toDouble()
        val rawEntropy = password.length * log2(poolSize.toDouble())
        val adjustedEntropy = rawEntropy * (0.55 + 0.45 * uniqueCharsRatio)

        val warnings = mutableListOf<String>()
        if (password.length < 12) {
            warnings.add("Doporučená minimální délka je alespoň 12 znaků")
        }
        if (!hasUpper || !hasLower) {
            warnings.add("Kombinujte malá a velká písmena")
        }
        if (!hasDigit) {
            warnings.add("Přidejte alespoň jednu číslici")
        }
        if (!hasSymbol && !password.contains("-")) {
            warnings.add("Přidejte speciální znak nebo pomlčku")
        }
        if (uniqueCharsRatio < 0.5) {
            warnings.add("Heslo obsahuje příliš mnoho opakujících se znaků")
        }

        val score = ((adjustedEntropy / 110.0) * 100.0).roundToInt().coerceIn(5, 100)
        val level = when {
            score < 40 -> PasswordStrengthLevel.WEAK
            score < 68 -> PasswordStrengthLevel.MEDIUM
            score < 88 -> PasswordStrengthLevel.STRONG
            else -> PasswordStrengthLevel.CRYPTOGRAPHIC
        }

        return PasswordStrengthReport(
            score = score,
            entropyBits = (adjustedEntropy * 10.0).roundToInt() / 10.0,
            level = level,
            warnings = warnings
        )
    }

    fun generatePassword(config: PasswordGeneratorConfig = PasswordGeneratorConfig()): String {
        if (config.passphraseMode) {
            val words = config.passphraseWordCount.coerceIn(3, 10)
            return (1..words).joinToString("-") {
                val word = dictionaryWords[secureRandom.nextInt(dictionaryWords.size)]
                if (config.includeUppercase && secureRandom.nextBoolean()) {
                    word.replaceFirstChar { it.uppercase() }
                } else {
                    word
                }
            } + if (config.includeDigits) "-${10 + secureRandom.nextInt(90)}" else ""
        }

        val targetLength = config.length.coerceIn(8, 64)
        val pools = mutableListOf<String>()
        if (config.includeUppercase) {
            pools.add(if (config.excludeAmbiguous) UPPER else UPPER + UPPER_AMBIGUOUS)
        }
        if (config.includeLowercase) {
            pools.add(if (config.excludeAmbiguous) LOWER else LOWER + LOWER_AMBIGUOUS)
        }
        if (config.includeDigits) {
            pools.add(if (config.excludeAmbiguous) DIGITS else DIGITS + DIGITS_AMBIGUOUS)
        }
        if (config.includeSymbols) {
            pools.add(SYMBOLS)
        }
        if (pools.isEmpty()) {
            pools.add(LOWER + DIGITS)
        }

        val combinedPool = pools.joinToString("")
        val chars = CharArray(targetLength)
        // Guarantee at least one character from each enabled pool
        for (i in pools.indices) {
            if (i < targetLength) {
                val pool = pools[i]
                chars[i] = pool[secureRandom.nextInt(pool.length)]
            }
        }
        for (i in pools.size until targetLength) {
            chars[i] = combinedPool[secureRandom.nextInt(combinedPool.length)]
        }
        // Fisher-Yates shuffle
        for (i in chars.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }
}
