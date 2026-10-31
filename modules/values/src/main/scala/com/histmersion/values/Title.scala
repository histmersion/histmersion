package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** What a record is called where it is shown: a source's or an article's title, an event's or a place's label. */
type Title = Title.T

object Title extends RefinedType[String, Pure]
