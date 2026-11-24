package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}

/** Book covers (`media.covers`): the Open Library Covers API and its own, slower pace. */
@movesTo(FeaturePlace.Media)
final case class MediaCoversConfig(
    openLibraryUrl: MediaCoversConfig.OpenLibraryUrl,
    openLibraryPage: MediaCoversConfig.OpenLibraryPage,
    requestIntervalMs: MediaCoversConfig.RequestIntervalMs,
    size: MediaCoversConfig.Size,
    thumbSize: MediaCoversConfig.ThumbSize
) derives ConfigReader

object MediaCoversConfig:
  type OpenLibraryUrl = OpenLibraryUrl.T
  object OpenLibraryUrl extends Newtype[String, Pure]
  type OpenLibraryPage = OpenLibraryPage.T
  object OpenLibraryPage extends Newtype[String, Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends Newtype[Int, Pure]
  type Size = Size.T
  object Size extends Newtype[String, Pure]
  type ThumbSize = ThumbSize.T
  object ThumbSize extends Newtype[String, Pure]
