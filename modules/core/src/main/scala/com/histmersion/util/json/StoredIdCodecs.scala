package com.histmersion
package util.json

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.automation.Run

/** Decoders for the ids a stored checkpoint, ledger or run output holds: they were plain text when those rows were
  * written, so a row still loads whatever its id looks like. Only data read from storage uses them; the ids a person or
  * a model hands in are checked where they come in.
  */
object StoredIdCodecs:
  given citekey: JsonValueCodec[Citekey] = JsonCodec.text[Citekey](_.value, s => Right(Citekey.assume(s)))

  given doi: JsonValueCodec[Doi] = JsonCodec.text[Doi](_.value, s => Right(Doi.assume(s)))

  given placeId: JsonValueCodec[Place.Id] = JsonCodec.text[Place.Id](_.value, s => Right(Place.Id.assume(s)))

  given runId: JsonValueCodec[Run.Id] = JsonCodec.text[Run.Id](_.value, s => Right(Run.Id.assume(s)))
