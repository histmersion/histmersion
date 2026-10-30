package com.histmersion.plugin

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A licence AS ITS PROVIDER STATES IT: an SPDX id (`CC-BY-4.0`, `CC0-1.0`), a short name (`CC BY-SA 3.0`,
  * `Public domain`), a Creative Commons URL, or the provider's own words. Never judged by the plugin: the host reads
  * the statement and decides what it allows, so any text is a statement (one the host cannot read is "unknown", which
  * it never stores).
  */
type Licence = Licence.T

object Licence extends RefinedType[String, Pure]
