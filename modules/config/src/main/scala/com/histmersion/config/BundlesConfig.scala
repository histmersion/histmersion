package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `bundle export|verify|import` (docs/research-merge-import.md): where the server builds a bundle before it is
  * downloaded and keeps an uploaded one while it is read, and how long a finished export is left there for its download
  * before the next export clears it.
  */
final case class BundlesConfig(dir: String, staleMinutes: Int) derives ConfigReader
