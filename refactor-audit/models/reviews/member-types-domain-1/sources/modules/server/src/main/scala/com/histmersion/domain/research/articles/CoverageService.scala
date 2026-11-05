package com.histmersion.domain.research.articles

import com.histmersion.api.library.Filter
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.util.database.Transactor
import com.histmersion.api.research.Coverage

/** What an article's coverage reads of the library's sources: a filter's source set, which of a set of citekeys it
  * returns, the disagreement edges touching the cited sources. A mechanism over the library's filter SQL, so it has no
  * in-memory twin.
  */
trait CoverageService:

  /** How many sources `filter` selects, and the first `cap` of them in citekey order. */
  def sourceSet(filter: Filter, scope: PlaceScope, cap: com.histmersion.domain.research.articles.CoverageService.SourceSetCap): (com.histmersion.domain.research.articles.CoverageService.SourceSetResultPart1, List[Coverage.Source])

  /** How many sources `filter` selects, and which of `citekeys` are among them. */
  def members(filter: Filter, scope: PlaceScope, citekeys: List[com.histmersion.domain.research.articles.CoverageService.MembersCitekeys]): (com.histmersion.domain.research.articles.CoverageService.MembersResultPart1, Set[com.histmersion.domain.research.articles.CoverageService.MembersResultPart2])

  /** The edges of `kinds` with either end in `citekeys`. */
  def edgesTouching(citekeys: List[com.histmersion.domain.research.articles.CoverageService.EdgesTouchingCitekeys], kinds: List[com.histmersion.domain.research.articles.CoverageService.EdgesTouchingKinds]): List[Coverage.Edge]

object CoverageService:

  type SourceSetResultPart1 = SourceSetResultPart1.T
  object SourceSetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceSetCap = SourceSetCap.T
  object SourceSetCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MembersResultPart1 = MembersResultPart1.T
  object MembersResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MembersResultPart2 = MembersResultPart2.T
  object MembersResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MembersCitekeys = MembersCitekeys.T
  object MembersCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgesTouchingCitekeys = EdgesTouchingCitekeys.T
  object EdgesTouchingCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgesTouchingKinds = EdgesTouchingKinds.T
  object EdgesTouchingKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): CoverageService = Implementation(db)

  final private class Implementation(db: Transactor) extends CoverageService:
    def sourceSet(filter: Filter, scope: PlaceScope, cap: com.histmersion.domain.research.articles.CoverageService.Implementation.SourceSetCap): (com.histmersion.domain.research.articles.CoverageService.Implementation.SourceSetResultPart1, List[Coverage.Source]) =
      db.read(CoverageRepo.sourceSet(filter, scope, cap))
    def members(filter: Filter, scope: PlaceScope, citekeys: List[com.histmersion.domain.research.articles.CoverageService.Implementation.MembersCitekeys]): (com.histmersion.domain.research.articles.CoverageService.Implementation.MembersResultPart1, Set[com.histmersion.domain.research.articles.CoverageService.Implementation.MembersResultPart2]) =
      db.read(CoverageRepo.members(filter, scope, citekeys))
    def edgesTouching(citekeys: List[com.histmersion.domain.research.articles.CoverageService.Implementation.EdgesTouchingCitekeys], kinds: List[com.histmersion.domain.research.articles.CoverageService.Implementation.EdgesTouchingKinds]): List[Coverage.Edge] =
      if citekeys.isEmpty || kinds.isEmpty then Nil else db.read(CoverageRepo.edgesTouching(citekeys, kinds))

  object Implementation:
    type SourceSetResultPart1 = SourceSetResultPart1.T
    object SourceSetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourceSetCap = SourceSetCap.T
    object SourceSetCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MembersResultPart1 = MembersResultPart1.T
    object MembersResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MembersResultPart2 = MembersResultPart2.T
    object MembersResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MembersCitekeys = MembersCitekeys.T
    object MembersCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EdgesTouchingCitekeys = EdgesTouchingCitekeys.T
    object EdgesTouchingCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EdgesTouchingKinds = EdgesTouchingKinds.T
    object EdgesTouchingKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
