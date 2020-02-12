package eu.kanade.tachiyomi.util.system

import android.webkit.WebView

private val WEBVIEW_UA_VERSION_REGEX by lazy {
    Regex(""".*Chrome/(\d+)\..*""")
}

private const val MINIMUM_WEBVIEW_VERSION = 70

fun WebView.isOutdated(): Boolean {
    return getWebviewMajorVersion(this) < MINIMUM_WEBVIEW_VERSION
}

// Based on https://stackoverflow.com/a/29218966
private fun getWebviewMajorVersion(webview: WebView): Int {
    val originalUA: String = webview.settings.userAgentString

    // Next call to getUserAgentString() will get us the default
    webview.settings.userAgentString = null

    val uaRegexMatch = WEBVIEW_UA_VERSION_REGEX.matchEntire(webview.settings.userAgentString)
    val webViewVersion: Int = if (uaRegexMatch != null && uaRegexMatch.groupValues.size > 1) {
        uaRegexMatch.groupValues[1].toInt()
    } else {
        0
    }

    // Revert to original UA string
    webview.settings.userAgentString = originalUA

    return webViewVersion
}
