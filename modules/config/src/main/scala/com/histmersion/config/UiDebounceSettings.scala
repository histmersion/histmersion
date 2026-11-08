package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `ui.debounce`: the quiet periods before the page acts on typing and state changes; `previewMs` is the one before a
  * HOVERED suggestion's preview reloads the lists.
  */
final case class UiDebounceSettings(suggestMs: UiDebounceSettings.SuggestMs, searchMs: UiDebounceSettings.SearchMs, reloadMs: UiDebounceSettings.ReloadMs, previewMs: UiDebounceSettings.PreviewMs) derives ConfigReader

object UiDebounceSettings:
  type SuggestMs = SuggestMs.T
  object SuggestMs extends RefinedType[Int, Pure]
  type SearchMs = SearchMs.T
  object SearchMs extends RefinedType[Int, Pure]
  type ReloadMs = ReloadMs.T
  object ReloadMs extends RefinedType[Int, Pure]
  type PreviewMs = PreviewMs.T
  object PreviewMs extends RefinedType[Int, Pure]
