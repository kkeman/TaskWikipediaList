package com.example.wikipedialist.view.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.wikipedialist.Constant
import com.example.wikipedialist.R
import com.example.wikipedialist.image.BitmapWorkerTask
import com.example.wikipedialist.model.RelatedModel
import com.example.wikipedialist.view.activity.ListActivity


class ListAdapter(var relatedList: MutableList<RelatedModel>, var searchWord: String) : BaseAdapter() {
    override fun getCount(): Int {
        return if (relatedList != null) {
            relatedList.size
        } else {
            0
        }
    }

    override fun getItem(position: Int): Any? {
        return if (relatedList != null) {
            relatedList.get(position)
        } else {
            null
        }
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val holder: ViewHolder

        if (convertView == null) {
            convertView = LayoutInflater.from(parent.context).inflate(R.layout.item_list, parent, false)
            holder = ViewHolder()
            holder.ivThumb = convertView.findViewById<View>(R.id.iv_thumb) as ImageView
            holder.tvTitle = convertView.findViewById<View>(R.id.tv_title) as TextView
            holder.tvExtract = convertView.findViewById<View>(R.id.tv_extract) as TextView
            convertView.tag = holder
        } else {
            holder = convertView.tag as ViewHolder
        }

        var thumbnail = relatedList.get(position).getThumbnail()
        if (thumbnail == null) thumbnail = Constant.DEFAULT_IMAGE
        BitmapWorkerTask.Companion.loadBitmap(parent.context, true, thumbnail, holder.ivThumb)

        holder.tvTitle.setText(relatedList.get(position).getTitle())
        holder.tvExtract.setText(relatedList.get(position).getExtract())

        convertView!!.setOnClickListener {
            val intent = Intent(parent.context, ListActivity::class.java)
            intent.putExtra(Constant.INTENT_EXTRA_SEARCH_WORD, searchWord)
            parent.context.startActivity(intent)
        }
        return convertView!!
    }

    internal class ViewHolder {
        lateinit var ivThumb: ImageView
        lateinit var tvTitle: TextView
        lateinit var tvExtract: TextView
    }
}