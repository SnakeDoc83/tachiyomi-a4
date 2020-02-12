package eu.kanade.tachiyomi.network

import okhttp3.*

interface A4OkHttp {
    @Suppress("unused")
    companion object {
        fun String.toMediaTypeOrNull(): MediaType? {
            return MediaType.parse(this)
        }

        fun String.toRequestBody(mediaType: MediaType?): RequestBody {
            return RequestBody.create(mediaType, this)
        }

        fun String.toHttpUrlOrNull(): HttpUrl? {
            return HttpUrl.parse(this)
        }

        fun String.toHttpUrl(): HttpUrl {
            return HttpUrl.parse(this)!!
        }

        val Response.body: ResponseBody?
            get() = this.body()

        val Request.body: RequestBody
            get() = this.body()!!

        val MediaType.type: String
            get() = this.type()

        val MediaType.subtype: String
            get() = this.subtype()

        val Response.code: Int
            get() = this.code()

        val FormBody.size: Int
            get() = this.size()

        val Request.url: HttpUrl
            get() = this.url()

        val Request.method: String
            get() = this.method()

        val Cookie.name: String
            get() = this.name()

        val Response.priorResponse: Response?
            get() = this.priorResponse()

        val Request.headers: Headers
            get() = this.headers()

        val ConnectionSpec.cipherSuites: MutableList<CipherSuite>?
            get() = this.cipherSuites()

        val Response.request: Request
            get() = this.request()
    }
}
