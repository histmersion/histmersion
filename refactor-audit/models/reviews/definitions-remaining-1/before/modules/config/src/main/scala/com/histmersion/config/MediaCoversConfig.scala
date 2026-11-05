package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Book covers (`media.covers`): the Open Library Covers API and its own, slower pace. */
final case class MediaCoversConfig(
    openLibraryUrl: String,
    openLibraryPage: String,
    requestIntervalMs: Int,
    size: String,
    thumbSize: String
) derives ConfigReader
