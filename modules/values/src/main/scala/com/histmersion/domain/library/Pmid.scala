package com.histmersion
package domain.library
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Pmid = Pmid.T

object Pmid extends Newtype[String, Pure]
