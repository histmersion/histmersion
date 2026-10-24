package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A DOI as the registry writes it after `https://doi.org/`: `10.<registrant>/<suffix>`. */
type Doi = Doi.T

object Doi extends RefinedType[String, Match["10\\.[0-9]+/\\S+"]]
