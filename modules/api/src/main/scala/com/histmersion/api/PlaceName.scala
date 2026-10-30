package com.histmersion.api

/** One name a place is known by. `lang` is a BCP-47-ish tag or `""` when unspecified; `period` is when the name was in
  * use (`None` = always), `open` the side of it its source left open.
  */
final case class PlaceName(
    name: String,
    lang: String = "",
    period: Option[Period] = None,
    open: Option[OpenSide] = None
):
  /** The inclusive bounds stored for it ([[OpenSide.bounds]]). */
  def bounds: (Option[Long], Option[Long]) = OpenSide.bounds(period, open)
