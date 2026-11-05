package com.histmersion.domain.library.sources

import com.augustnagro.magnum.DbCon

/** How another domain reads the sources' citekeys inside its own transaction: the citekey each id names now (an old
  * citekey answers with the one it was renamed to).
  */
trait CitekeyResolver:

  /** The citekey each of `ids` names now; an id that names nothing is absent. */
  def canonical(ids: List[com.histmersion.domain.library.sources.CitekeyResolver.CanonicalIds])(using DbCon): Map[com.histmersion.domain.library.sources.CitekeyResolver.CanonicalResultPart1, com.histmersion.domain.library.sources.CitekeyResolver.CanonicalResultPart2]

object CitekeyResolver:

  /** The stored sources and their old citekeys. */
  type CanonicalResultPart1 = CanonicalResultPart1.T
  object CanonicalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanonicalResultPart2 = CanonicalResultPart2.T
  object CanonicalResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanonicalIds = CanonicalIds.T
  object CanonicalIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val stored: CitekeyResolver = new CitekeyResolver:
    def canonical(ids: List[com.histmersion.domain.library.sources.CitekeyResolver.CanonicalIds])(using DbCon): Map[com.histmersion.domain.library.sources.CitekeyResolver.CanonicalResultPart1, com.histmersion.domain.library.sources.CitekeyResolver.CanonicalResultPart2] = SourceRepo.canonicalAll(ids)
