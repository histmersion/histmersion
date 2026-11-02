package com.histmersion.domain.library
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** An ISBN as given, with or without hyphens. */
type Isbn = Isbn.T

object Isbn extends RefinedType[String, Pure]
