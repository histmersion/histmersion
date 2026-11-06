package com.histmersion.util.json

import io.circe.Decoder

/** Decoders for the ids a stored checkpoint, ledger or run output holds: they were plain text when those rows were
  * written, so a row still loads whatever its id looks like. Only data read from storage uses them; the ids a person
  * or a model hands in are checked where they come in.
  */
object StoredIdCodecs:
  given citekey: Decoder[com.histmersion.domain.library.Citekey] =
    Decoder.decodeString.map(com.histmersion.domain.library.Citekey.assume(_))

  given doi: Decoder[com.histmersion.domain.library.Doi] =
    Decoder.decodeString.map(com.histmersion.domain.library.Doi.assume(_))

  given placeId: Decoder[com.histmersion.domain.catalogue.Place.Id] =
    Decoder.decodeString.map(com.histmersion.domain.catalogue.Place.Id.assume(_))
