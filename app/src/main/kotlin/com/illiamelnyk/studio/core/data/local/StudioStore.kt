package com.illiamelnyk.studio.core.data.local

import android.content.Context
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

class StudioStore(context: Context) {
    private val file = File(context.filesDir, "studio-state-v1.json")
    private var root = try {
        JSONObject(file.readText())
    } catch (_: java.io.FileNotFoundException) {
        JSONObject()
    } catch (error: org.json.JSONException) {
        android.util.Log.e("StudioData", "Cannot read cached state", error)
        JSONObject()
    }

    @Synchronized
    fun read(): JSONObject = JSONObject(root.toString())

    @Synchronized
    fun change(block: (JSONObject) -> Unit) {
        val updated = JSONObject(root.toString())
        block(updated)
        val temporary = File(file.parentFile, file.name + ".new")
        try {
            temporary.outputStream().use { stream ->
                stream.write(updated.toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            root = updated
        } finally {
            temporary.delete()
        }
    }
}

internal fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
internal fun JSONObject.stringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf(String::isNotEmpty)
