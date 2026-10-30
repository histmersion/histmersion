package com.histmersion.api

enum PlaceOverrideEdit:
  case Reset

  case Put(
      kind: Option[String],
      period: Option[(Option[Long], Option[Long])],
      reason: Option[String],
      point: Option[GeoPoint] = None
  )
