package com.histmersion.domain.catalogue.places

import com.augustnagro.magnum.*
import com.histmersion.domain.catalogue.Place

private[places] object PlaceColumns:
  /** `places.location_precision` (V077): NULL is a point as stated; an unknown value is the LESS precise grid cell — a
    * point no nearness may be read from — not "as stated".
    */
  given given_DbCodec_PlacePrecision: DbCodec[Place.Precision] = DbCodec[Option[String]].biMap(
    s => s.fold(Place.Precision.Stated)(v => Place.Precision.parse(Place.Precision.Wire(v.trim)).getOrElse(Place.Precision.GridCell)),
    p => Option.when(p != Place.Precision.Stated)(p.wire.value)
  )
