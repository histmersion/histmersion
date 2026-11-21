package com.histmersion
package domain.library
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Pmcid = Pmcid.T

object Pmcid extends Newtype[String, Pure]
