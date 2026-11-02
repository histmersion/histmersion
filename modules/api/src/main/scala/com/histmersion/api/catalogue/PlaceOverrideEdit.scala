package com.histmersion.api.catalogue

import com.histmersion.api.GeoPoint
import com.histmersion.api.given
enum PlaceOverrideEdit:
  case Reset

  case Put(
      kind: Option[String],
      period: Option[(Option[Long], Option[Long])],
      reason: Option[String],
      point: Option[GeoPoint] = None
  )
