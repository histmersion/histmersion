package com.histmersion.api

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
