package com.histmersion.util.identity

import io.github.iltotore.iron.*

/** What a reader calls a record of a kind ("source", "article"). */
type RecordNoun = RecordNoun.T
object RecordNoun extends RefinedType[String, Pure]
