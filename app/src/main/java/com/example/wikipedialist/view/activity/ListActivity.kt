package com.example.wikipedialist.view.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableArrayList
import androidx.lifecycle.Observer
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.wikipedialist.Constant
import com.example.wikipedialist.R
import com.example.wikipedialist.databinding.ActivityListBinding
import com.example.wikipedialist.image.BitmapWorkerTask
import com.example.wikipedialist.model.RelatedModel
import com.example.wikipedialist.view.adapter.ListAdapter
import com.example.wikipedialist.viewmodel.ListViewModel


class ListActivity : AppCompatActivity() {

    private lateinit var swipeLayout: SwipeRefreshLayout

    private lateinit var vHeader: View

    private val mTag = ListActivity::class.java.name
    private lateinit var ivThumb: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvExtract: TextView

    private lateinit var mBinding: ActivityListBinding
    private lateinit var mViewModel: ListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_list)
        mBinding.viewModel = ListViewModel()
        mViewModel = mBinding.viewModel!!

        searchWord = intent.getStringExtra(Constant.INTENT_EXTRA_SEARCH_WORD)!!

        title = searchWord
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)


        swipeLayout = findViewById(R.id.swipe_layout)


        vHeader = LayoutInflater.from(this).inflate(R.layout.item_header, null)
        mBinding.listview.addHeaderView(vHeader)
        ivThumb = vHeader.findViewById<View>(R.id.iv_thumb) as ImageView
        tvTitle = vHeader.findViewById<View>(R.id.tv_title) as TextView
        tvExtract = vHeader.findViewById<View>(R.id.tv_extract) as TextView
        vHeader.setOnClickListener {
            val intent = Intent(this@ListActivity, SearchDetailActivity::class.java)
            intent.putExtra(Constant.INTENT_EXTRA_SEARCH_WORD, searchWord)
            startActivity(intent)
        }

        mViewModel.setRelatedAPI(searchWord)
        mViewModel.setSummaryAPI(searchWord)



        mViewModel.summaryModel.observe(this, Observer {
            BitmapWorkerTask.loadBitmap(this@ListActivity, true, it.getThumbnail(), ivThumb)

            tvTitle.text = it.getDisplaytitle()

            val text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Html.fromHtml(it.getExtract_html(), Html.FROM_HTML_MODE_COMPACT) else Html.fromHtml(it.getExtract_html())
            tvExtract.text = text.toString()
        })
    }

    companion object {

        private lateinit var searchWord: String

        @BindingAdapter("app:items")
        @JvmStatic
        fun setList(listView: ListView, users: ObservableArrayList<RelatedModel>) {

            val listAdapter = ListAdapter(users, searchWord)
            listView.adapter = listAdapter

            listAdapter.notifyDataSetChanged()
        }
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

