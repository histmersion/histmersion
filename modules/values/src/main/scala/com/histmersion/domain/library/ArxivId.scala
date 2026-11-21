package com.histmersion
package domain.library
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type ArxivId = ArxivId.T

object ArxivId extends Newtype[String, Pure]
