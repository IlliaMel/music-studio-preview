package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import android.content.pm.PackageManager

internal class TrustedMediaCaller(private val context: Context) {
    fun accepts(packageName: String, uid: Int): Boolean {
        val packages = context.packageManager.getPackagesForUid(uid).orEmpty()
        if (packageName !in packages) return false
        if (uid == context.applicationInfo.uid && packageName == context.packageName) return true
        if (packageName !in PLATFORM_CLIENTS) return false
        return context.packageManager.checkSignatures("android", packageName) == PackageManager.SIGNATURE_MATCH
    }

    companion object {
        private val PLATFORM_CLIENTS = setOf("com.android.systemui", "android")
    }
}
