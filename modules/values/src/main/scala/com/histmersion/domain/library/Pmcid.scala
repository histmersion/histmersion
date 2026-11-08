package com.histmersion
package domain.library
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A PubMed Central id as given (`PMC1234567`). */
type Pmcid = Pmcid.T

object Pmcid extends RefinedType[String, Pure]
