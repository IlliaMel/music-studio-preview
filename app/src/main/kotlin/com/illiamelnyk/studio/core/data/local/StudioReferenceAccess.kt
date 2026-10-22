package com.illiamelnyk.studio.core.data.local

import java.io.File

/** Main-safe owner-fenced directory access for recording; preparation happens on the data dispatcher. */
interface StudioReferenceAccess {
    fun referenceDirectory(): File
}
