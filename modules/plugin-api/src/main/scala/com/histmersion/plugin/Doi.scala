package com.histmersion.plugin

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A DOI as the registry writes it after `https://doi.org/`: `10.<registrant>/<suffix>` (`10.1234/abc`). */
type Doi = Doi.T

object Doi extends RefinedType[String, Match["10\\.[0-9]+/\\S+"]]
