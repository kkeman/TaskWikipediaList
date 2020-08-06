package com.example.wikipedialist.view.activity

import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.wikipedialist.Constant
import com.example.wikipedialist.R


class SearchDetailActivity : AppCompatActivity() {

    private var searchWord: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_detail)

        searchWord = intent.getStringExtra(Constant.INTENT_EXTRA_SEARCH_WORD)

        title = searchWord
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val webView = findViewById<WebView>(R.id.wv_search_detail)
        webView.webViewClient = WebViewClient()
        webView.loadUrl(Constant.API_HTML + "/" + searchWord)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}