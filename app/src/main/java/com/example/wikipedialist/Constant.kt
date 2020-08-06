package com.example.wikipedialist

object Constant {
    val URL_HOST_HTTPS = "https://en.wikipedia.org/api/rest_v1/page"
    val API_HTML = URL_HOST_HTTPS + "/html"
    val API_SUMMARY = URL_HOST_HTTPS + "/summary"
    val API_RELATED = URL_HOST_HTTPS + "/related"
    const val mTimeout = 10000
    val INTENT_EXTRA_SEARCH_WORD = "IntentExtraSearchWord"
    val DEFAULT_IMAGE = "https://storage.googleapis.com/gweb-uniblog-publish-prod/images/Android_symbol_green_2.max-500x500.png"
}