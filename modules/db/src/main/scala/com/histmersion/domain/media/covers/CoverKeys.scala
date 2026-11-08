package com.histmersion
package domain.media.covers

import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A book's identifiers the Open Library Covers API answers by. */
final case class CoverKeys(citekey: Citekey, isbn: Option[CoverKeys.Isbn], oclc: Option[CoverKeys.Oclc])

object CoverKeys:
  type Isbn = Isbn.T
  object Isbn extends RefinedType[String, Pure]
  type Oclc = Oclc.T
  object Oclc extends RefinedType[String, Pure]
