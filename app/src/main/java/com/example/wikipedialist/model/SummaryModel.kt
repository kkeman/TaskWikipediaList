package com.example.wikipedialist.model



class SummaryModel(displaytitle: String, extract_html: String, thumbnail: String) {
    private val displaytitle: String
    private val extract_html: String
    private val thumbnail: String
    fun getDisplaytitle(): String {
        return displaytitle
    }

    fun getExtract_html(): String {
        return extract_html
    }

    fun getThumbnail(): String {
        return thumbnail
    }

    init {
        this.displaytitle = displaytitle
        this.extract_html = extract_html
        this.thumbnail = thumbnail
    }
}