package com.example.wikipedialist.view.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.example.wikipedialist.Constant
import com.example.wikipedialist.R


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.b_search).setOnClickListener { view ->
            val intent = Intent(this@MainActivity, ListActivity::class.java)
            intent.putExtra(Constant.INTENT_EXTRA_SEARCH_WORD, (findViewById<View?>(R.id.et_search) as EditText).getText().toString())
            startActivity(intent)
        }
    }
}