package com.histmersion
package domain.library.findings

import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.util.database.Transactor
import com.histmersion.api.library.{Filter, GeoHit}
import com.histmersion.lint.{LintRule, tolerated}

/** The map feed (`geo-when`): the findings that themselves satisfy a filter's finding-level criteria, of the sources
  * that satisfy its source-level ones, and how many. A mechanism over the filter's SQL (PostGIS boxes, the gazetteer's
  * scope), so it has no in-memory twin.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.Service, "pending G16 (a service is a trait with Implementation and InMemory)")
trait FindingService:
  def feed(filter: Filter, scope: PlaceScope): List[GeoHit]
  def count(filter: Filter, scope: PlaceScope): RowCount

object FindingService:

  def apply(db: Transactor): FindingService = Implementation(db)

  final private class Implementation(db: Transactor) extends FindingService:
    def feed(filter: Filter, scope: PlaceScope): List[GeoHit] = db.read(FindingRepo.findingsFor(filter, scope)).toList
    def count(filter: Filter, scope: PlaceScope): RowCount = db.read(FindingRepo.findingsCount(filter, scope))
