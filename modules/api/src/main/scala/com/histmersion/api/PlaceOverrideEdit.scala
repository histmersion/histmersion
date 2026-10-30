package com.histmersion.api

/** A validated [[PlaceOverrideSet]]. */
enum PlaceOverrideEdit:
  /** Drop the override; the source's kind and period come back. */
  case Reset

  /** Set the kind and/or the period (`(start, end)`, `None` = open on that side) and/or the point; `None` keeps what
    * the override had (or the source's).
    */
  case Put(
      kind: Option[String],
      period: Option[(Option[Long], Option[Long])],
      reason: Option[String],
      point: Option[GeoPoint] = None
  )
