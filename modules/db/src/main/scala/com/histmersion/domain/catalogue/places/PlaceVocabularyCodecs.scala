package com.histmersion.domain.catalogue.places

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.Vocab
import com.histmersion.api.catalogue.Place

/** An unknown dating is `unknown` (it used to read as `stated`, a date the place does not have). */
given given_DbCodec_PlaceDating: DbCodec[Place.Dating] = Vocab.codec(Place.Dating.parse, _.wire, Place.Dating.Unknown)

/** The basis is computed in SQL ([[PlaceRepo]]), so every value is known; `dated` is the basis that claims nothing
  * beyond the place's own period.
  */
given given_DbCodec_PlaceTimeBasis: DbCodec[Place.TimeBasis] =
  Vocab.codec(Place.TimeBasis.parse, _.wire, Place.TimeBasis.Dated)
