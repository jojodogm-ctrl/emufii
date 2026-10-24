package eu.emufii.app.onetap

import eu.emufii.app.BuildConfig
import java.net.URI
import java.net.URLEncoder

/** Empty configuration keeps undeployed adapter/frontend actions disabled. */
class OneTapConfiguration(adapterUrl: String, frontendUrl: String) {
    val adapterUrl: String? = configuredUrl(adapterUrl, "")
    val frontendUrl: String? = configuredUrl(frontendUrl, "/emufii/play/")

    fun createHostUrl(game: OneTapGame, name: String): String? = frontendUrl?.let {
        "$it?game=${game.wireName}&name=${URLEncoder.encode(name, Charsets.UTF_8.name())}"
    }

    companion object {
        fun fromBuild(): OneTapConfiguration = OneTapConfiguration(
            BuildConfig.ONE_TAP_ADAPTER_URL, BuildConfig.ONE_TAP_FRONTEND_URL
        )

        private fun configuredUrl(value: String, path: String): String? {
            if (value.isEmpty()) return null
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            return value.takeIf {
                uri.scheme == "https" && !uri.host.isNullOrEmpty() && uri.rawUserInfo == null && uri.port == -1 &&
                    uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.orEmpty() == path &&
                    uri.toASCIIString() == value
            }
        }
    }
}
