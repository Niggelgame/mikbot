package dev.schlaubi.mikbot.core.i18n.database

import com.kotlindiscord.kord.extensions.builders.ExtensibleBotBuilder
import dev.kord.common.Locale
import dev.schlaubi.mikbot.plugin.api.Plugin
import dev.schlaubi.mikbot.plugin.api.PluginMain
import dev.schlaubi.mikbot.plugin.api.PluginWrapper

@PluginMain
class DatabaseI18NPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {
    override suspend fun ExtensibleBotBuilder.apply() {
        i18n {
            defaultLocale = Config.DEFAULT_LOCALE

            localeResolver { _, _, user, interaction ->
                user?.let {
                    LanguageDatabase.collection.findOneById(it.id)?.locale
                } ?: run {
                    interaction?.locale?.sanitize()?.asJavaLocale()
                }
            }
        }
    }
}

// Discord only gives you the language, not the country
private fun Locale.sanitize() = when {
    country != null -> this
    language == "de" -> Locale(language, "DE")
    language == "it" -> Locale(language, "IT")
    language == "en" -> Locale(language, "GB")
    else -> this
}