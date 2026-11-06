package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

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
  object OpenLibraryUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenLibraryPage = OpenLibraryPage.T
  object OpenLibraryPage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Size = Size.T
  object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbSize = ThumbSize.T
  object ThumbSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
