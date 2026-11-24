package com.histmersion
package domain.media.covers

import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** A book's identifiers the Open Library Covers API answers by. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class CoverKeys(citekey: Citekey, isbn: Option[CoverKeys.Isbn], oclc: Option[CoverKeys.Oclc])

object CoverKeys:
  type Isbn = Isbn.T
  object Isbn extends Newtype[String, Pure]
  type Oclc = Oclc.T
  object Oclc extends Newtype[String, Pure]
