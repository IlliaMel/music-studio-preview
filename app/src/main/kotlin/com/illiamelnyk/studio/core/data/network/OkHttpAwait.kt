package com.illiamelnyk.studio.core.data.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Keeps cancellation attached to the socket until bounded body consumption finishes, not headers. */
internal suspend fun Call.awaitBoundedBody(maxBytes: Int, validate: (Response) -> Unit): ByteArray {
    require(maxBytes in 1 until Int.MAX_VALUE)
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        if (!continuation.isActive) return
                        validate(response)
                        val input = response.body?.byteStream() ?: throw IOException("Missing audio response")
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        // One extra byte lets the caller reject oversize audio without buffering it.
                        while (total <= maxBytes) {
                            if (!continuation.isActive) return
                            val count = input.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - total))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            total += count
                        }
                        output.toByteArray()
                    }
                    if (continuation.isActive) continuation.resume(bytes)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
}
