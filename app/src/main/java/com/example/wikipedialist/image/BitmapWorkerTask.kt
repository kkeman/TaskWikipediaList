package com.example.wikipedialist.image

import android.app.ActivityManager
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.AsyncTask
import android.widget.ImageView
import androidx.collection.LruCache
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URL

class BitmapWorkerTask(imageView: ImageView) : AsyncTask<String, Void, Bitmap>() {
    private val weakReference: WeakReference<ImageView>
    private var path: String = ""
    override fun doInBackground(vararg strings: String): Bitmap? {
        return try {
            path = strings[0]
            var bitmap = getBitmapFromCache(path)
            if (bitmap != null) {
                return bitmap
            }
            val imgFile = File(path)
            bitmap = if (imgFile.exists()) BitmapFactory.decodeFile(imgFile.absolutePath) else BitmapFactory.decodeStream(URL(path).openStream())
            if (isReduction) bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, false)
            addBitmapToCache(path, bitmap)
            bitmap
        } catch (e: IOException) {
            null
        }
    }

    fun getBitmapFromCache(key: String): Bitmap? {
        var bitmap = getBitmapFromMemCache(key)
        if (bitmap == null) {
            bitmap = getBitmapFromDiskCache(key)
            bitmap?.let { addBitmapToCache(key, it) }
        }
        return bitmap
    }

    fun addBitmapToCache(key: String, bitmap: Bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            addBitmapToMemoryCache(key, bitmap)
        }
        if (getBitmapFromDiskCache(key) == null) {
            addBitmapToDiskCache(bitmap, key)
        }
    }

    override fun onPostExecute(bitmap: Bitmap?) {
        var bitmap = bitmap
        if (isCancelled) {
            bitmap = null
        }
        if (weakReference != null && bitmap != null) {
            val imageView = weakReference.get()
            val bitmapWorkerTask = getBitmapWorkerTask(imageView)
            if (this === bitmapWorkerTask && imageView != null) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    fun addBitmapToMemoryCache(key: String, bitmap: Bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            cache?.put(key, bitmap)
        }
    }

    fun addBitmapToDiskCache(bitmap: Bitmap, key: String) {
        try {
            val editor = diskCache!!.edit(key.hashCode().toString() + "")
            if (editor != null) {
                val os = editor.newOutputStream(0)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                editor.commit()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getBitmapFromMemCache(key: String): Bitmap? {
        return cache!!.get(key)
    }

    fun getBitmapFromDiskCache(key: String): Bitmap? {
        var bitmap: Bitmap? = null
        try {
            val snapshot = diskCache!!.get(key.hashCode().toString() + "")
            if (snapshot != null) {
                bitmap = BitmapFactory.decodeStream(snapshot.getInputStream(0))
            }
        } catch (e: IOException) {
            bitmap = null
        }
        return bitmap
    }

    class AsyncDrawable(res: Resources?, bitmap: Bitmap?,
                        bitmapWorkerTask: BitmapWorkerTask) : BitmapDrawable(res, bitmap) {
        private val bitmapWorkerTaskReference: WeakReference<BitmapWorkerTask>
        fun getBitmapWorkerTask(): BitmapWorkerTask? {
            return bitmapWorkerTaskReference.get()
        }

        init {
            bitmapWorkerTaskReference = WeakReference(bitmapWorkerTask)
        }
    }

    companion object {
        private var cache: LruCache<String, Bitmap>? = null
        private var diskCache: DiskLruCache? = null
        private var isReduction = false
        private fun getBitmapWorkerTask(imageView: ImageView?): BitmapWorkerTask? {
            if (imageView != null) {
                val drawable = imageView.drawable
                if (drawable is AsyncDrawable) {
                    val asyncDrawable = drawable as AsyncDrawable
                    return asyncDrawable.getBitmapWorkerTask()
                }
            }
            return null
        }

        fun cancelPotentialWork(url: String?, imageView: ImageView?): Boolean {
            val bitmapWorkerTask = getBitmapWorkerTask(imageView)
            if (bitmapWorkerTask != null) {
                val bitmapData = bitmapWorkerTask.path
                if (bitmapData != url) {
                    // Cancel previous task
                    bitmapWorkerTask.cancel(true)
                } else {
                    return false
                }
            }
            return true
        }

        fun loadBitmap(context: Context, reduction: Boolean, url: String, imageView: ImageView) {
            isReduction = reduction
            if (cancelPotentialWork(url, imageView)) {
                val task = BitmapWorkerTask(imageView)
                val asyncDrawable = AsyncDrawable(context.getResources(), null, task)
                imageView.setImageDrawable(asyncDrawable)
                task.execute(url)
            }
        }
    }

    init {
        if (cache == null) {
            val memClass = (imageView.getContext().getSystemService(
                    Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
            cache = object : LruCache<String, Bitmap>(1024 * 1024 * memClass / 3) {
                protected override fun sizeOf(key: String, value: Bitmap): Int {
                    return value.getRowBytes() * value.getHeight()
                }
            }
        }
        if (diskCache == null) {
            try {
                diskCache = DiskLruCache.Companion.open(imageView.getContext().cacheDir, 1, 1, 1024 * 1024 * 10.toLong())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        weakReference = WeakReference(imageView)
    }
}