package com.histmersion
package util.json

import io.circe.Decoder
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.automation.Run

/** Decoders for the ids a stored checkpoint, ledger or run output holds: they were plain text when those rows were
  * written, so a row still loads whatever its id looks like. Only data read from storage uses them; the ids a person or
  * a model hands in are checked where they come in.
  */
object StoredIdCodecs:
  given citekey: Decoder[Citekey] =
    Decoder.decodeString.map(Citekey.assume(_))

  given doi: Decoder[Doi] =
    Decoder.decodeString.map(Doi.assume(_))

  given placeId: Decoder[Place.Id] =
    Decoder.decodeString.map(Place.Id.assume(_))

  given runId: Decoder[Run.Id] =
    Decoder.decodeString.map(Run.Id.assume(_))
