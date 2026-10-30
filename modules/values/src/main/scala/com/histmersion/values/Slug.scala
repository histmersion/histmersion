package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** An article's slug: a URL segment and a file name, lower-case, 1 to 121 characters (`sea_peoples`, `uluburun.ship`). */
type Slug = Slug.T

object Slug extends RefinedType[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]
