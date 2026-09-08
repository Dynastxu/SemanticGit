package com.github.semanticgit.ui

import java.util.Locale

object I18n {

    private val translations = mutableMapOf<String, String>()

    var locale: Locale = Locale.SIMPLIFIED_CHINESE
        private set

    fun setLocale(locale: Locale) {
        this.locale = locale
        translations.clear()
        translations.putAll(loadTranslations(locale))
    }

    fun t(key: String): String = translations[key] ?: key

    private fun loadTranslations(locale: Locale): Map<String, String> {
        val fileName = when {
            locale == Locale.SIMPLIFIED_CHINESE || locale.language == "zh" -> "zh_cn"
            else -> "en_us"
        }
        val path = "com/github/semanticgit/ui/i18n/$fileName.json"
        val content = javaClass.classLoader.getResourceAsStream(path)?.bufferedReader()?.readText()
            ?: return emptyMap()

        return parseJson(content)
    }

    private fun parseJson(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val regex = """"([^"]+)"\s*:\s*"([^"]*)"""".toRegex()
        regex.findAll(content).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].replace("\\n", "\n").replace("\\t", "\t")
            result[key] = value
        }
        return result
    }

    init {
        setLocale(Locale.SIMPLIFIED_CHINESE)
    }
}
