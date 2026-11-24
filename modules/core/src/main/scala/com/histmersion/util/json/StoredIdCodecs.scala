package com.histmersion
package util.json

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.automation.Run
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** Decoders for the ids a stored checkpoint, ledger or run output holds: they were plain text when those rows were
  * written, so a row still loads whatever its id looks like. Only data read from storage uses them; the ids a person or
  * a model hands in are checked where they come in.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@atBoundary(BoundaryKind.Json, "decodes the ids held in stored checkpoint and run JSON")
object StoredIdCodecs:
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  given citekey: JsonValueCodec[Citekey] = JsonCodec.text[Citekey](_.value, s => Right(Citekey.assume(s)))

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  given doi: JsonValueCodec[Doi] = JsonCodec.text[Doi](_.value, s => Right(Doi.assume(s)))

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  given placeId: JsonValueCodec[Place.Id] = JsonCodec.text[Place.Id](_.value, s => Right(Place.Id.assume(s)))

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  given runId: JsonValueCodec[Run.Id] = JsonCodec.text[Run.Id](_.value, s => Right(Run.Id.assume(s)))
