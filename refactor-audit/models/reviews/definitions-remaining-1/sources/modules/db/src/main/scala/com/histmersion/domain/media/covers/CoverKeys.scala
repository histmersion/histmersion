package com.histmersion.domain.media.covers

/** A book's identifiers the Open Library Covers API answers by. */
final case class CoverKeys(citekey: com.histmersion.domain.library.Citekey, isbn: Option[CoverKeys.Isbn], oclc: Option[CoverKeys.Oclc])

object CoverKeys:
  type Isbn = Isbn.T
  object Isbn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Oclc = Oclc.T
  object Oclc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
