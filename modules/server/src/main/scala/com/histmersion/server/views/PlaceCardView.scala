package com.histmersion.server.views

import com.histmersion.api.{ApiError, MediaThumb, PlaceCounts, PlaceDetail}
import hearth.kindlings.optics.syntax.*

/** A place's page as the gazetteer, the corpus and the pictures show it together (the web UI's map hover card): the
  * gazetteer's `place` (the row, its geometry and the hierarchy around it), and — with `counts` — what `place=<id>`
  * selects in each list (`counts` of the place's id now) and the place's pictures.
  */
final class PlaceCardView(
    place: (String, Option[Long]) => Either[ApiError, PlaceDetail],
    now: String => String,
    counts: String => PlaceCounts,
    pictures: String => List[MediaThumb]
):
  def apply(id: String, asOf: Option[Long], withCounts: Boolean): Either[ApiError, PlaceDetail] =
    place(id, asOf).map(d =>
      if withCounts then d.modify(_.counts).setTo(Some(counts(now(id)))).modify(_.media).setTo(pictures(d.place.id))
      else d
    )
