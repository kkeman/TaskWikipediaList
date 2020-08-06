package com.example.wikipedialist.image

import java.io.*
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class DiskLruCache private constructor(
        private val directory: File, private val appVersion: Int, valueCount: Int, maxSize: Long) : Closeable {
    private var journalFile: File
    private var journalFileTmp: File
    private var journalFileBackup: File
    private var maxSize: Long
    private var valueCount: Int
    private var size: Long = 0
    private var journalWriter: Writer? = null
    private var lruEntries: LinkedHashMap<String, Entry> = LinkedHashMap(0, 0.75f, true)
    private var redundantOpCount = 0

    private var nextSequenceNumber: Long = 0

    val executorService: ThreadPoolExecutor = ThreadPoolExecutor(0, 1, 60L, TimeUnit.SECONDS, LinkedBlockingQueue())
    private val cleanupCallable: Callable<Void?>? = Callable {
        synchronized(this@DiskLruCache) {
            if (journalWriter == null) {
                return@Callable null // Closed.
            }
            trimToSize()
            if (journalRebuildRequired()) {
                rebuildJournal()
                redundantOpCount = 0
            }
        }
        null
    }

    @Throws(IOException::class)
    private fun readJournal() {
        val reader = StrictLineReader(FileInputStream(journalFile), Util.US_ASCII)
        try {
            val magic = reader.readLine()
            val version = reader.readLine()
            val appVersionString = reader.readLine()
            val valueCountString = reader.readLine()
            val blank = reader.readLine()
            if (MAGIC != magic
                    || VERSION_1 != version
                    || Integer.toString(appVersion) != appVersionString
                    || Integer.toString(valueCount) != valueCountString
                    || "" != blank) {
                throw IOException("unexpected journal header: [" + magic + ", " + version + ", "
                        + valueCountString + ", " + blank + "]")
            }
            var lineCount = 0
            while (true) {
                try {
                    readJournalLine(reader.readLine())
                    lineCount++
                } catch (endOfJournal: EOFException) {
                    break
                }
            }
            redundantOpCount = lineCount - lruEntries.size


            if (reader.hasUnterminatedLine()) {
                rebuildJournal()
            } else {
                journalWriter = BufferedWriter(OutputStreamWriter(
                        FileOutputStream(journalFile, true), Util.US_ASCII))
            }
        } finally {
            Util.closeQuietly(reader)
        }
    }

    @Throws(IOException::class)
    private fun readJournalLine(line: String) {
        val firstSpace = line.indexOf(' ')
        if (firstSpace == -1) {
            throw IOException("unexpected journal line: $line")
        }
        val keyBegin = firstSpace + 1
        val secondSpace = line.indexOf(' ', keyBegin)
        val key: String
        if (secondSpace == -1) {
            key = line.substring(keyBegin)
            if (firstSpace == REMOVE.length && line.startsWith(REMOVE)) {
                lruEntries.remove(key)
                return
            }
        } else {
            key = line.substring(keyBegin, secondSpace)
        }
        var entry = lruEntries[key]
        if (entry == null) {
            entry = Entry(key)
            lruEntries[key] = entry
        }
        if (secondSpace != -1 && firstSpace == CLEAN.length && line.startsWith(CLEAN)) {
            val parts: Array<String> = line.substring(secondSpace + 1).split(" ".toRegex()).toTypedArray()
            entry.readable = true
            entry.currentEditor = null
            entry.setLengths(parts)
        } else if (secondSpace == -1 && firstSpace == DIRTY.length && line.startsWith(DIRTY)) {
            entry.currentEditor = Editor(entry)
        } else if (secondSpace == -1 && firstSpace == READ.length && line.startsWith(READ)) {
            // This work was already done by calling lruEntries.get().
        } else {
            throw IOException("unexpected journal line: $line")
        }
    }

    @Throws(IOException::class)
    private fun processJournal() {
        deleteIfExists(journalFileTmp)
        val i = lruEntries.values.iterator()
        while (i.hasNext()) {
            val entry = i.next()
            if (entry.currentEditor == null) {
                for (t in 0 until valueCount) {
                    size += entry.lengths.get(t)
                }
            } else {
                entry.currentEditor = null
                for (t in 0 until valueCount) {
                    deleteIfExists(entry.getCleanFile(t))
                    deleteIfExists(entry.getDirtyFile(t))
                }
                i.remove()
            }
        }
    }

    @Synchronized
    @Throws(IOException::class)
    private fun rebuildJournal() {
        if (journalWriter != null) {
            journalWriter!!.close()
        }
        val writer: Writer = BufferedWriter(
                OutputStreamWriter(FileOutputStream(journalFileTmp), Util.US_ASCII))
        try {
            writer.write(MAGIC)
            writer.write("\n")
            writer.write(VERSION_1)
            writer.write("\n")
            writer.write(Integer.toString(appVersion))
            writer.write("\n")
            writer.write(Integer.toString(valueCount))
            writer.write("\n")
            writer.write("\n")
            for (entry in lruEntries.values) {
                if (entry.currentEditor != null) {
                    writer.write("""$DIRTY ${entry.key}
""")
                } else {
                    writer.write("""$CLEAN ${entry.key}${entry.getLengths()}
""")
                }
            }
        } finally {
            writer.close()
        }
        if (journalFile.exists()) {
            renameTo(journalFile, journalFileBackup, true)
        }
        renameTo(journalFileTmp, journalFile, false)
        journalFileBackup.delete()
        journalWriter = BufferedWriter(
                OutputStreamWriter(FileOutputStream(journalFile, true), Util.US_ASCII))
    }

    @Synchronized
    @Throws(IOException::class)
    operator fun get(key: String): Snapshot? {
        checkNotClosed()
        validateKey(key)
        val entry = lruEntries[key] ?: return null
        if (!entry.readable) {
            return null
        }

        val ins = arrayOfNulls<InputStream>(valueCount)
        try {
            for (i in 0 until valueCount) {
                ins[i] = FileInputStream(entry.getCleanFile(i))
            }
        } catch (e: FileNotFoundException) {
            var i = 0
            while (i < valueCount) {
                if (ins[i] != null) {
                    Util.closeQuietly(ins[i])
                } else {
                    break
                }
                i++
            }
            return null
        }
        redundantOpCount++
        journalWriter!!.append("""$READ $key
""")
        if (journalRebuildRequired()) {
            executorService.submit(cleanupCallable)
        }
        return Snapshot(key, entry.sequenceNumber, ins, entry.lengths)
    }

    @Throws(IOException::class)
    fun edit(key: String): Editor? {
        return edit(key, ANY_SEQUENCE_NUMBER)
    }

    @Synchronized
    @Throws(IOException::class)
    private fun edit(key: String, expectedSequenceNumber: Long): Editor? {
        checkNotClosed()
        validateKey(key)
        var entry = lruEntries[key]
        if (expectedSequenceNumber != ANY_SEQUENCE_NUMBER && (entry == null
                        || entry.sequenceNumber != expectedSequenceNumber)) {
            return null // Snapshot is stale.
        }
        if (entry == null) {
            entry = Entry(key)
            lruEntries[key] = entry
        } else if (entry.currentEditor != null) {
            return null
        }
        val editor: Editor = Editor(entry)
        entry.currentEditor = editor

        journalWriter!!.write("""$DIRTY $key
""")
        journalWriter!!.flush()
        return editor
    }

    fun getDirectory(): File? {
        return directory
    }

    @Synchronized
    fun getMaxSize(): Long {
        return maxSize
    }

    @Synchronized
    fun setMaxSize(maxSize: Long) {
        this.maxSize = maxSize
        executorService.submit(cleanupCallable)
    }

    @Synchronized
    fun size(): Long {
        return size
    }

    @Synchronized
    @Throws(IOException::class)
    private fun completeEdit(editor: Editor, success: Boolean) {
        val entry = editor.entry
        check(entry.currentEditor == editor)

        if (success && !entry.readable) {
            for (i in 0 until valueCount) {
                if (!editor.written.get(i)) {
                    editor.abort()
                    throw IllegalStateException("Newly created entry didn't create value for index $i")
                }
                if (!entry.getDirtyFile(i).exists()) {
                    editor.abort()
                    return
                }
            }
        }
        for (i in 0 until valueCount) {
            val dirty = entry.getDirtyFile(i)
            if (success) {
                if (dirty.exists()) {
                    val clean = entry.getCleanFile(i)
                    dirty.renameTo(clean)
                    val oldLength = entry.lengths.get(i)
                    val newLength = clean.length()
                    entry.lengths.set(i, newLength)
                    size = size - oldLength + newLength
                }
            } else {
                deleteIfExists(dirty)
            }
        }
        redundantOpCount++
        entry.currentEditor = null
        if (entry.readable or success) {
            entry.readable = true
            journalWriter!!.write("""$CLEAN ${entry.key}${entry.getLengths()}""")
            if (success) {
                entry.sequenceNumber = nextSequenceNumber++
            }
        } else {
            lruEntries.remove(entry.key)
            journalWriter!!.write("""$REMOVE ${entry.key}""")
        }
        journalWriter!!.flush()
        if (size > maxSize || journalRebuildRequired()) {
            executorService.submit(cleanupCallable)
        }
    }

    private fun journalRebuildRequired(): Boolean {
        val redundantOpCompactThreshold = 2000
        return (redundantOpCount >= redundantOpCompactThreshold //
                && redundantOpCount >= lruEntries.size)
    }

    @Synchronized
    @Throws(IOException::class)
    fun remove(key: String?): Boolean {
        checkNotClosed()
        validateKey(key)
        val entry = lruEntries[key]
        if (entry == null || entry.currentEditor != null) {
            return false
        }
        for (i in 0 until valueCount) {
            val file = entry.getCleanFile(i)
            if (file.exists() && !file.delete()) {
                throw IOException("failed to delete $file")
            }
            size -= entry.lengths.get(i)
            entry.lengths.set(i, 0)
        }
        redundantOpCount++
        journalWriter!!.append("""$REMOVE $key""")
        lruEntries.remove(key)
        if (journalRebuildRequired()) {
            executorService.submit(cleanupCallable)
        }
        return true
    }

    @Synchronized
    fun isClosed(): Boolean {
        return journalWriter == null
    }

    private fun checkNotClosed() {
        checkNotNull(journalWriter) { "cache is closed" }
    }

    @Synchronized
    @Throws(IOException::class)
    fun flush() {
        checkNotClosed()
        trimToSize()
        journalWriter!!.flush()
    }

    @Synchronized
    @Throws(IOException::class)
    override fun close() {
        if (journalWriter == null) {
            return  // Already closed.
        }
        for (entry in ArrayList(lruEntries.values)) {
            if (entry.currentEditor != null) {
                entry.currentEditor!!.abort()
            }
        }
        trimToSize()
        journalWriter!!.close()
    }

    @Throws(IOException::class)
    private fun trimToSize() {
        while (size > maxSize) {
            val toEvict = lruEntries.entries.iterator().next()
            remove(toEvict.key)
        }
    }

    @Throws(IOException::class)
    fun delete() {
        close()
        Util.deleteContents(directory)
    }

    private fun validateKey(key: String?) {
        val matcher = LEGAL_KEY_PATTERN.matcher(key)
        require(matcher.matches()) {
            ("keys must match regex "
                    + STRING_KEY_PATTERN + ": \"" + key + "\"")
        }
    }

    inner class Snapshot constructor(private val key: String, private val sequenceNumber: Long, private val ins: Array<InputStream?>, private val lengths: LongArray) : Closeable {

        @Throws(IOException::class)
        fun edit(): Editor? {
            return this@DiskLruCache.edit(key, sequenceNumber)
        }

        fun getInputStream(index: Int): InputStream? {
            return ins.get(index)
        }

        @Throws(IOException::class)
        fun getString(index: Int): String? {
            return inputStreamToString(getInputStream(index))
        }

        fun getLength(index: Int): Long {
            return lengths.get(index)
        }

        override fun close() {
            for (`in` in ins) {
                Util.closeQuietly(`in`)
            }
        }
    }

    inner class Editor constructor(public val entry: Entry) {
        val written: BooleanArray
        private var hasErrors = false
        private var committed = false

        @Throws(IOException::class)
        fun newInputStream(index: Int): InputStream? {
            synchronized(this@DiskLruCache) {
                check(entry.currentEditor == this)
                return if (!entry.readable) {
                    null
                } else try {
                    FileInputStream(entry.getCleanFile(index))
                } catch (e: FileNotFoundException) {
                    null
                }
            }
        }

        @Throws(IOException::class)
        fun getString(index: Int): String? {
            val `in` = newInputStream(index)
            return if (`in` != null) inputStreamToString(`in`) else null
        }

        @Throws(IOException::class)
        fun newOutputStream(index: Int): OutputStream {
            require(!(index < 0 || index >= valueCount)) {
                ("Expected index " + index + " to "
                        + "be greater than 0 and less than the maximum value count "
                        + "of " + valueCount)
            }
            synchronized(this@DiskLruCache) {
                check(entry.currentEditor == this)
                if (!entry.readable) {
                    written.set(index, true)
                }
                val dirtyFile = entry.getDirtyFile(index)
                val outputStream: FileOutputStream
                outputStream = try {
                    FileOutputStream(dirtyFile)
                } catch (e: FileNotFoundException) {
                    directory.mkdirs()
                    try {
                        FileOutputStream(dirtyFile)
                    } catch (e2: FileNotFoundException) {
                        return NULL_OUTPUT_STREAM
                    }
                }
                return FaultHidingOutputStream(outputStream)
            }
        }

        @Throws(IOException::class)
        operator fun set(index: Int, value: String?) {
            var writer: Writer? = null
            try {
                writer = OutputStreamWriter(newOutputStream(index), Util.UTF_8)
                writer.write(value)
            } finally {
                Util.closeQuietly(writer)
            }
        }

        @Throws(IOException::class)
        fun commit() {
            if (hasErrors) {
                completeEdit(this, false)
                remove(entry.key) // The previous entry is stale.
            } else {
                completeEdit(this, true)
            }
            committed = true
        }

        @Throws(IOException::class)
        fun abort() {
            completeEdit(this, false)
        }

        fun abortUnlessCommitted() {
            if (!committed) {
                try {
                    abort()
                } catch (ignored: IOException) {
                }
            }
        }

        private inner class FaultHidingOutputStream internal constructor(out: OutputStream) : FilterOutputStream(out) {
            override fun write(oneByte: Int) {
                try {
                    out.write(oneByte)
                } catch (e: IOException) {
                    hasErrors = true
                }
            }

            override fun write(buffer: ByteArray?, offset: Int, length: Int) {
                try {
                    out.write(buffer, offset, length)
                } catch (e: IOException) {
                    hasErrors = true
                }
            }

            override fun close() {
                try {
                    out.close()
                } catch (e: IOException) {
                    hasErrors = true
                }
            }

            override fun flush() {
                try {
                    out.flush()
                } catch (e: IOException) {
                    hasErrors = true
                }
            }
        }

        init {
            written = if (entry.readable) BooleanArray(0) else BooleanArray(valueCount)
        }
    }

    public inner class Entry constructor(val key: String) {
        val lengths: LongArray

        var readable = false

        var currentEditor: Editor? = null

        internal var sequenceNumber: Long = 0

        @Throws(IOException::class)
        fun getLengths(): String? {
            val result = StringBuilder()
            for (size in lengths) {
                result.append(' ').append(size)
            }
            return result.toString()
        }

        @Throws(IOException::class)
        fun setLengths(strings: Array<String>) {
            if (strings.size != valueCount) {
                throw invalidLengths(strings)
            }
            try {
                for (i in strings.indices) {
                    lengths.set(i, strings.get(i).toLong())
                }
            } catch (e: NumberFormatException) {
                throw invalidLengths(strings)
            }
        }

        @Throws(IOException::class)
        private fun invalidLengths(strings: Array<String>): IOException {
            throw IOException("unexpected journal line: " + Arrays.toString(strings))
        }

        fun getCleanFile(i: Int): File {
            return File(directory, "$key.$i")
        }

        fun getDirtyFile(i: Int): File {
            return File(directory, "$key.$i.tmp")
        }

        init {
            lengths = LongArray(valueCount)
        }
    }

    companion object {
        val JOURNAL_FILE: String = "journal"
        val JOURNAL_FILE_TEMP: String = "journal.tmp"
        val JOURNAL_FILE_BACKUP: String = "journal.bkp"
        val MAGIC: String = "libcore.io.DiskLruCache"
        val VERSION_1: String = "1"
        const val ANY_SEQUENCE_NUMBER: Long = -1
        val STRING_KEY_PATTERN: String = "[a-z0-9_-]{1,120}"
        val LEGAL_KEY_PATTERN = Pattern.compile(STRING_KEY_PATTERN)
        private val CLEAN: String = "CLEAN"
        private val DIRTY: String = "DIRTY"
        private val REMOVE: String = "REMOVE"
        private val READ: String = "READ"

        @Throws(IOException::class)
        fun open(directory: File, appVersion: Int, valueCount: Int, maxSize: Long): DiskLruCache {
            require(maxSize > 0) { "maxSize <= 0" }
            require(valueCount > 0) { "valueCount <= 0" }

            val backupFile = File(directory, JOURNAL_FILE_BACKUP)
            if (backupFile.exists()) {
                val journalFile = File(directory, JOURNAL_FILE)
                if (journalFile.exists()) {
                    backupFile.delete()
                } else {
                    renameTo(backupFile, journalFile, false)
                }
            }

            var cache = DiskLruCache(directory, appVersion, valueCount, maxSize)
            if (cache.journalFile.exists()) {
                try {
                    cache.readJournal()
                    cache.processJournal()
                    return cache
                } catch (journalIsCorrupt: IOException) {
                    println("DiskLruCache "
                            + directory
                            + " is corrupt: "
                            + journalIsCorrupt.message
                            + ", removing")
                    cache.delete()
                }
            }

            directory.mkdirs()
            cache = DiskLruCache(directory, appVersion, valueCount, maxSize)
            cache.rebuildJournal()
            return cache
        }

        @Throws(IOException::class)
        private fun deleteIfExists(file: File) {
            if (file.exists() && !file.delete()) {
                throw IOException()
            }
        }

        @Throws(IOException::class)
        private fun renameTo(from: File, to: File, deleteDestination: Boolean) {
            if (deleteDestination) {
                deleteIfExists(to)
            }
            if (!from.renameTo(to)) {
                throw IOException()
            }
        }

        @Throws(IOException::class)
        private fun inputStreamToString(`in`: InputStream?): String? {
            return Util.readFully(InputStreamReader(`in`, Util.UTF_8))
        }

        private val NULL_OUTPUT_STREAM: OutputStream = object : OutputStream() {
            @Throws(IOException::class)
            override fun write(b: Int) {
            }
        }
    }

    init {
        journalFile = File(directory, JOURNAL_FILE)
        journalFileTmp = File(directory, JOURNAL_FILE_TEMP)
        journalFileBackup = File(directory, JOURNAL_FILE_BACKUP)
        this.valueCount = valueCount
        this.maxSize = maxSize
    }
}