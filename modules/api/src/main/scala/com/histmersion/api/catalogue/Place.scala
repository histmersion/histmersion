package com.histmersion.api.catalogue

import com.histmersion.api.OpenSide
import com.histmersion.api.given
final case class Place(
    id: String,
    label: String,
    kind: String,
    geometry: Option[String] = None,
    period: Option[Period] = None,
    source: Option[String] = None,
    properties: Option[String] = None,
    open: Option[OpenSide] = None
):
  def bounds: (Option[Long], Option[Long]) = OpenSide.bounds(period, open)
