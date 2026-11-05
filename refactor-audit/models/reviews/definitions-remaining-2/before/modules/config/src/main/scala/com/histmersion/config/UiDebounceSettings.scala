package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `ui.debounce`: the quiet periods before the page acts on typing and state changes; `previewMs` is the one before a
  * HOVERED suggestion's preview reloads the lists.
  */
final case class UiDebounceSettings(suggestMs: Int, searchMs: Int, reloadMs: Int, previewMs: Int) derives ConfigReader
