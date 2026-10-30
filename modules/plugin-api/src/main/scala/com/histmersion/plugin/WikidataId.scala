package com.histmersion.plugin

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A Wikidata item: `Q` and its number (`Q5788`). */
type WikidataId = WikidataId.T

object WikidataId extends RefinedType[String, Match["Q[1-9][0-9]*"]]
