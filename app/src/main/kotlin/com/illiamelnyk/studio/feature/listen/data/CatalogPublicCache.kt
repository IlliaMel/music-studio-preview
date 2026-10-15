package com.illiamelnyk.studio.feature.listen.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.json.JSONObject

internal class CatalogPublicCache(context: Context, private val origin: String) {
    private val key = MessageDigest.getInstance("SHA-256").digest(origin.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private val file = AtomicFile(File(context.filesDir, "public-catalog-v1-" + key + ".json"))

    fun read(): JSONObject? = try {
        if (file.baseFile.length() > MAX_BYTES) throw IOException("Catalog cache exceeds limit")
        JSONObject(file.openRead().bufferedReader().use { it.readText() }).also { CatalogDocument.parse(it, origin) }
    } catch (_: Exception) {
        null
    }

    fun write(document: JSONObject) {
        CatalogDocument.parse(document, origin)
        val bytes = document.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (failure: Exception) {
            file.failWrite(output)
            throw failure
        }
    }

    private companion object {
        const val MAX_BYTES = 1024 * 1024
    }
}
