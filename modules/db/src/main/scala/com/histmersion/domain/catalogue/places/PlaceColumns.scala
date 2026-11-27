package com.histmersion
package domain.catalogue.places

import com.augustnagro.magnum.*
import com.histmersion.domain.catalogue.Place
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

@atBoundary(BoundaryKind.Sql, "reads and writes the place precision column")
private[places] object PlaceColumns:
  /** `places.location_precision` (V077): NULL is a point as stated; an unknown value is the LESS precise grid cell — a
    * point no nearness may be read from — not "as stated".
    */
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  given given_DbCodec_PlacePrecision: DbCodec[Place.Precision] = DbCodec[Option[String]].biMap(
    s =>
      s.fold(Place.Precision.Stated)(v =>
        Place.Precision.parse(Place.Precision.Wire(v.trim)).getOrElse(Place.Precision.GridCell)
      ),
    p => Option.when(p != Place.Precision.Stated)(p.wire.value)
  )
