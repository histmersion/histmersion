package com.histmersion
package domain.library.findings

import com.histmersion.domain.catalogue.places.PlaceScope

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.library.{Filter, GeoHit}

/** The db suites' reads of the map feed on a database of their own. */
object FindingFixtures:
  def findingsFor(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): Vector[GeoHit] =
    connect(ds)(FindingRepo.findingsFor(filter, scope))
  def findingsCount(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): RowCount =
    connect(ds)(FindingRepo.findingsCount(filter, scope))
