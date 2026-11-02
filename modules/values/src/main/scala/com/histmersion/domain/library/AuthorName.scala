package com.histmersion.domain.library
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** One author of a source, as the record names them (`Kristiansen, K.`). */
type AuthorName = AuthorName.T

object AuthorName extends RefinedType[String, Pure]
