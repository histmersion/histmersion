package com.histmersion.plugin

import scala.compiletime.deferred

object Spi:
  /** Bumped on an incompatible change to anything in this package. A new extension point (a new registry on
    * [[PluginContext]]) is compatible: existing plugins never call it.
    *
    * An `inline val`: the number is compiled INTO the plugin, so the host reads the generation the plugin was built
    * against, not its own. (SPI 1 declared a plain `val`, read from the host at run time; an SPI-1 plugin fails to link
    * against SPI 2 on exactly that read, which the host reports as such.)
    *
    * SPI 2 gave the values the SPI exchanges types of their own ([[Year]], [[Period]], [[Latitude]], [[Longitude]],
    * [[Doi]], [[WikidataId]], [[Licence]]).
    */
  inline val Version = 2
