package com.illiamelnyk.studio.feature.create.domain

/** Remembers the educational prompt answer; OS permission is requested by the route. */
interface NotificationChoice {
    suspend fun hasAnswered(): Boolean

    suspend fun answer()
}
