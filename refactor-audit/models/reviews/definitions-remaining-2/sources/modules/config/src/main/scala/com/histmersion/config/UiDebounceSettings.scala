package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `ui.debounce`: the quiet periods before the page acts on typing and state changes; `previewMs` is the one before a
  * HOVERED suggestion's preview reloads the lists.
  */
final case class UiDebounceSettings(suggestMs: UiDebounceSettings.SuggestMs, searchMs: UiDebounceSettings.SearchMs, reloadMs: UiDebounceSettings.ReloadMs, previewMs: UiDebounceSettings.PreviewMs) derives ConfigReader

object UiDebounceSettings:
  type SuggestMs = SuggestMs.T
  object SuggestMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchMs = SearchMs.T
  object SearchMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReloadMs = ReloadMs.T
  object ReloadMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PreviewMs = PreviewMs.T
  object PreviewMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
