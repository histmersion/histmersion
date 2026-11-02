package com.histmersion.api.catalogue

import com.histmersion.api.OpenSide
import com.histmersion.api.given
final case class PlaceName(
    name: String,
    lang: String = "",
    period: Option[Period] = None,
    open: Option[OpenSide] = None
):
  def bounds: (Option[Long], Option[Long]) = OpenSide.bounds(period, open)
