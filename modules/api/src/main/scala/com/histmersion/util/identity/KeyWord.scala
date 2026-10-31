package com.histmersion.util.identity

import io.github.iltotore.iron.*

/** What a reader calls a record's identifier ("citekey", "slug", "id"). */
type KeyWord = KeyWord.T
object KeyWord extends RefinedType[String, Pure]
