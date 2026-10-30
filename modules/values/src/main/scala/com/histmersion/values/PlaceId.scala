package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A place's id: the gazetteer it comes from, an underscore, that gazetteer's id (`geonames_2911611`). */
type PlaceId = PlaceId.T

object PlaceId extends RefinedType[String, Match["[a-z][a-z0-9]*_.+"]]
