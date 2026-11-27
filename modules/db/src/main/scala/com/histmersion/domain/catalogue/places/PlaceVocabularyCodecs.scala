package com.histmersion
package domain.catalogue.places

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.Vocab
import com.histmersion.api.catalogue.Place
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** An unknown dating is `unknown` (it used to read as `stated`, a date the place does not have). */
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
@atBoundary(BoundaryKind.Sql, "reads and writes the place vocabularies as column strings")
given given_DbCodec_PlaceDating: DbCodec[Place.Dating] = Vocab.codec(
  s => Place.Dating.parse(Place.Dating.Wire(s.value)),
  d => WireEnumValues.Wire(d.wire.value),
  Place.Dating.Unknown
)

/** The basis is computed in SQL ([[PlaceRepo]]), so every value is known; `dated` is the basis that claims nothing
  * beyond the place's own period.
  */
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
@tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
given given_DbCodec_PlaceTimeBasis: DbCodec[Place.TimeBasis] =
  Vocab.codec(
    s => Place.TimeBasis.parse(Place.TimeBasis.Wire(s.value)),
    b => WireEnumValues.Wire(b.wire.value),
    Place.TimeBasis.Dated
  )
