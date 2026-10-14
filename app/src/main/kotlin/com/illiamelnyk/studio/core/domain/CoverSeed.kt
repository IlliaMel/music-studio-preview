package com.illiamelnyk.studio.core.domain

/**
 * What generated art (a track cover, an avatar) is drawn from. The same seed always gives the same
 * art, so a track keeps its cover everywhere without storing an image.
 */
@JvmInline
value class CoverSeed(val value: String)
