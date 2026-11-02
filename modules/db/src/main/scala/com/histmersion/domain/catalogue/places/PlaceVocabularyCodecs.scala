package com.histmersion.domain.catalogue.places

import com.augustnagro.magnum.DbCodec
import com.histmersion.api.{PlaceDating, PlaceTimeBasis}
import com.histmersion.db.Vocab

/** An unknown dating is `unknown` (it used to read as `stated`, a date the place does not have). */
given DbCodec[PlaceDating] = Vocab.codec(PlaceDating.parse, _.wire, PlaceDating.Unknown)

/** The basis is computed in SQL ([[PlaceRepo]]), so every value is known; `dated` is the basis that claims nothing
  * beyond the place's own period.
  */
given DbCodec[PlaceTimeBasis] = Vocab.codec(PlaceTimeBasis.parse, _.wire, PlaceTimeBasis.Dated)
