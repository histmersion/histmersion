package com.histmersion.domain.library
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A PubMed id as given. */
type Pmid = Pmid.T

object Pmid extends RefinedType[String, Pure]
