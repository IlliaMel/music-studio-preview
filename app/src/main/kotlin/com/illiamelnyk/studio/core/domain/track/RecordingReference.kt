package com.illiamelnyk.studio.core.domain.track

/** An owned saved recording selected for the normal temporary reference-upload flow. */
data class RecordingReference(val id: TrackId, val ownerId: String, val uri: String, val title: String)
