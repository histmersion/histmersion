package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Book covers (`media.covers`): the Open Library Covers API and its own, slower pace. */
final case class MediaCoversConfig(
    openLibraryUrl: MediaCoversConfig.OpenLibraryUrl,
    openLibraryPage: MediaCoversConfig.OpenLibraryPage,
    requestIntervalMs: MediaCoversConfig.RequestIntervalMs,
    size: MediaCoversConfig.Size,
    thumbSize: MediaCoversConfig.ThumbSize
) derives ConfigReader

object MediaCoversConfig:
  type OpenLibraryUrl = OpenLibraryUrl.T
  object OpenLibraryUrl extends RefinedType[String, Pure]
  type OpenLibraryPage = OpenLibraryPage.T
  object OpenLibraryPage extends RefinedType[String, Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends RefinedType[Int, Pure]
  type Size = Size.T
  object Size extends RefinedType[String, Pure]
  type ThumbSize = ThumbSize.T
  object ThumbSize extends RefinedType[String, Pure]
