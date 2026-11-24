package com.histmersion
package domain.research.articles

import com.histmersion.api.library.Filter
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.util.database.Transactor
import com.histmersion.api.research.Coverage
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Edge
import com.histmersion.lint.{LintRule, tolerated}

/** What an article's coverage reads of the library's sources: a filter's source set, which of a set of citekeys it
  * returns, the disagreement edges touching the cited sources. A mechanism over the library's filter SQL, so it has no
  * in-memory twin.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.Service, "pending G16 (a service is a trait with Implementation and InMemory)")
trait CoverageService:

  /** How many sources `filter` selects, and the first `cap` of them in citekey order. */
  def sourceSet(filter: Filter, scope: PlaceScope, cap: ArticleRepo.Limit): (RowCount, List[Coverage.Source])

  /** How many sources `filter` selects, and which of `citekeys` are among them. */
  def members(filter: Filter, scope: PlaceScope, citekeys: List[Citekey]): (RowCount, Set[Citekey])

  /** The edges of `kinds` with either end in `citekeys`. */
  def edgesTouching(citekeys: List[Citekey], kinds: List[Edge.Kind.Wire]): List[Coverage.Edge]

object CoverageService:

  def apply(db: Transactor): CoverageService = Implementation(db)

  final private class Implementation(db: Transactor) extends CoverageService:
    def sourceSet(filter: Filter, scope: PlaceScope, cap: ArticleRepo.Limit): (RowCount, List[Coverage.Source]) =
      db.read(CoverageRepo.sourceSet(filter, scope, cap))
    def members(filter: Filter, scope: PlaceScope, citekeys: List[Citekey]): (RowCount, Set[Citekey]) =
      db.read(CoverageRepo.members(filter, scope, citekeys))
    def edgesTouching(citekeys: List[Citekey], kinds: List[Edge.Kind.Wire]): List[Coverage.Edge] =
      if citekeys.isEmpty || kinds.isEmpty then Nil else db.read(CoverageRepo.edgesTouching(citekeys, kinds))
