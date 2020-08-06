package com.example.wikipedialist.viewmodel

import android.content.ContentValues
import androidx.databinding.ObservableArrayList
import androidx.databinding.ObservableBoolean
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.restapi.AsyncApi
import com.example.wikipedialist.Constant
import com.example.wikipedialist.model.RelatedModel
import com.example.wikipedialist.model.SummaryModel
import org.json.JSONObject


class ListViewModel : ViewModel() {

    val items: ObservableArrayList<RelatedModel> = ObservableArrayList()
    val isLoading: ObservableBoolean = ObservableBoolean()
    val summaryModel = MutableLiveData<SummaryModel>()

    fun setRelatedAPI(searchWord: String) {
        startAPI(Constant.API_RELATED + "/" + searchWord, AsyncApi.REQUEST_METHOD_GET, Constant.mTimeout, null, null)
    }

    fun setSummaryAPI(searchWord: String) {
        startAPI(Constant.API_SUMMARY + "/" + searchWord, AsyncApi.REQUEST_METHOD_GET, Constant.mTimeout, null, null)
    }

    private fun startAPI(url: String, requestMethod: String, timeout: Int, params: ContentValues?, header: MutableMap<String, String>?) {
        isLoading.set(true)

        val asyncApi = AsyncApi(url, requestMethod, params)
        asyncApi.setTimeout(timeout)
        asyncApi.setHeader(header)
        asyncApi.setCallback(object : AsyncApi.CallbackObjectResponse {
            override fun onResponse(result: JSONObject) {

                try {
                    if (url.contains(Constant.API_SUMMARY)) {
                        val title = result.optString("title")
                        val extract = result.optString("extract")

                        val thumbnail = if (result.has("thumbnail")) result.optJSONObject("thumbnail").optString("source") else Constant.DEFAULT_IMAGE

                        summaryModel.value = SummaryModel(title, extract, thumbnail)



                    } else if (url.contains(Constant.API_RELATED)) {
                        val array = result.getJSONArray("pages")

                        val relatedList: MutableList<RelatedModel> = ArrayList()

                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            val title = item.optString("title")
                            val extract = item.optString("extract")

                            var thumbnail = if (item.has("thumbnail")) item.optJSONObject("thumbnail").optString("source") else Constant.DEFAULT_IMAGE

                            relatedList.add(RelatedModel(title, extract, thumbnail))
                        }

                        items.addAll(relatedList)

                        isLoading.set(false)
                    }
                } catch (e: Exception) {
                    isLoading.set(false)
                    e.printStackTrace()
                }
            }

            override fun onError(error: Exception?) {
                isLoading.set(false)
            }
        })
        asyncApi.connectAsync()
    }
}
