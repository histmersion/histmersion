package com.histmersion.plugins.gazetteers

import hearth.kindlings.sconfigderivation.ConfigReader

/** A gazetteer plugin's settings: none of its own beyond the host's `enabled`. */
final case class GazetteerSettings() derives ConfigReader
