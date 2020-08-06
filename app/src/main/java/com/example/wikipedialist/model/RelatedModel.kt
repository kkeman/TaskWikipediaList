package com.example.wikipedialist.model



class RelatedModel(title: String, extract: String, thumbnail: String) {
    private val title: String
    private val extract: String
    private val thumbnail: String
    fun getTitle(): String {
        return title
    }

    fun getExtract(): String {
        return extract
    }

    fun getThumbnail(): String {
        return thumbnail
    }

    init {
        this.title = title
        this.extract = extract
        this.thumbnail = thumbnail
    }
}