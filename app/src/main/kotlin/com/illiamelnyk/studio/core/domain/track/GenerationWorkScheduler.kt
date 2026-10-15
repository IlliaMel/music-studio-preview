package com.illiamelnyk.studio.core.domain.track

interface GenerationWorkScheduler {
    fun reconcile(ownerId: String?)
    fun complete(ownerId: String) = reconcile(null)

    data object None : GenerationWorkScheduler {
        override fun reconcile(ownerId: String?) = Unit
    }
}
