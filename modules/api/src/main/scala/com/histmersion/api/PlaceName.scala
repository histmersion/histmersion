package com.histmersion.api

final case class PlaceName(
    name: String,
    lang: String = "",
    period: Option[Period] = None,
    open: Option[OpenSide] = None
):
  def bounds: (Option[Long], Option[Long]) = OpenSide.bounds(period, open)
