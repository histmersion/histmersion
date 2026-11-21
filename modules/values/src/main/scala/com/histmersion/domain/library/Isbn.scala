package com.histmersion
package domain.library
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Isbn = Isbn.T

object Isbn extends Newtype[String, Pure]
