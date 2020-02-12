package eu.kanade.tachiyomi.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.WebViewClientCompat
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import eu.kanade.tachiyomi.network.A4OkHttp.Companion.code
import eu.kanade.tachiyomi.network.A4OkHttp.Companion.url
import eu.kanade.tachiyomi.network.A4OkHttp.Companion.headers
import eu.kanade.tachiyomi.network.A4OkHttp.Companion.name
import eu.kanade.tachiyomi.network.A4OkHttp.Companion.toHttpUrl
import eu.kanade.tachiyomi.util.toast
import okhttp3.Cookie
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.util.system.isOutdated

class CloudflareInterceptor(private val context: Context) : Interceptor {

    private val handler = Handler(Looper.getMainLooper())

    private val networkHelper: NetworkHelper by injectLazy()

    /**
     * When this is called, it initializes the WebView if it wasn't already. We use this to avoid
     * blocking the main thread too much. If used too often we could consider moving it to the
     * Application class.
     */
    private val initWebView by lazy {
        if (Build.VERSION.SDK_INT >= 17) {
            WebSettings.getDefaultUserAgent(context)
        } else {
            null
        }
    }

    @Synchronized
    override fun intercept(chain: Interceptor.Chain): Response {
        initWebView

        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)

        // Check if Cloudflare anti-bot is on
        if (response.code != 503 || response.header("Server") !in SERVER_CHECK) {
            return response
        }

        try {
            response.close()
            networkHelper.cookieManager.remove(originalRequest.url, COOKIE_NAMES, 0)
            val oldCookie = networkHelper.cookieManager.get(originalRequest.url)
                    .firstOrNull { it.name == "cf_clearance" }
            resolveWithWebView(originalRequest, oldCookie)
            return chain.proceed(originalRequest)
        } catch (e: Exception) {
            // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
            // we don't crash the entire app
            throw IOException(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(request: Request, oldCookie: Cookie?) {
        // We need to lock this thread until the WebView finds the challenge solution url, because
        // OkHttp doesn't support asynchronous interceptors.
        val latch = CountDownLatch(1)

        var webView: WebView? = null

        var challengeFound = false
        var cloudflareBypassed = false
        var isWebviewOutdated = false

        val origRequestUrl = request.url.toString()
        val headers = request.headers.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }

        handler.post {
            val webview = WebView(context)
            webView = webview

            webview.settings.javaScriptEnabled = true
            webview.settings.userAgentString = request.header("User-Agent")

            webview.webViewClient = object : WebViewClientCompat() {
                override fun onPageFinished(view: WebView, url: String) {
                    fun isCloudFlareBypassed(): Boolean {
                        return networkHelper.cookieManager.get(origRequestUrl.toHttpUrl())
                                .firstOrNull { it.name == "cf_clearance" }
                                .let { it != null && it != oldCookie }
                    }

                    if (isCloudFlareBypassed()) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }
                    // Http error codes are only received since M
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            url == origRequestUrl && !challengeFound
                    ) {
                        // The first request didn't return the challenge, abort.
                        latch.countDown()
                    }
                }

                override fun onReceivedErrorCompat(
                        view: WebView,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String,
                        isMainFrame: Boolean
                ) {
                    if (isMainFrame) {
                        if (errorCode == 503) {
                            // Found the cloudflare challenge page.
                            challengeFound = true
                        } else {
                            // Unlock thread, the challenge wasn't found.
                            latch.countDown()
                        }
                    }
                }
            }

            webView?.loadUrl(origRequestUrl, headers)
        }

        // Wait a reasonable amount of time to retrieve the solution. The minimum should be
        // around 4 seconds but it can take more due to slow networks or server issues.
        latch.await(12, TimeUnit.SECONDS)

        handler.post {
            if (!cloudflareBypassed) {
                isWebviewOutdated = webView?.isOutdated() == true
            }

            webView?.stopLoading()
            webView?.destroy()
        }

        // Throw exception if we failed to bypass Cloudflare
        if (!cloudflareBypassed) {
            // Prompt user to update WebView if it seems too outdated
            if (isWebviewOutdated) {
                context.toast(R.string.information_webview_outdated, Toast.LENGTH_LONG)
            }

            throw Exception(context.getString(R.string.information_cloudflare_bypass_failure))
        }
    }

    companion object {
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        private val COOKIE_NAMES = listOf("__cfduid", "cf_clearance")
    }

}