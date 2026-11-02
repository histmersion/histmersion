package com.histmersion.server.views

import com.histmersion.api.media.MediaThumb
import com.histmersion.api.views.PlaceDetail
import hearth.kindlings.optics.syntax.*
import io.scalaland.chimney.dsl.*
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Place

/** A place's page as the gazetteer, the corpus and the pictures show it together (the web UI's map hover card): the
  * gazetteer's `place` (the row, its geometry and the hierarchy around it), and — with `counts` — what `place=<id>`
  * selects in each list (`counts` of the place's id now) and the place's pictures.
  */
final class PlaceCardView(
    place: (String, Option[Long]) => Either[Api.Error, Place.Entry],
    now: String => String,
    counts: String => Place.Counts,
    pictures: String => List[MediaThumb]
):
  def apply(id: String, asOf: Option[Long], withCounts: Boolean): Either[Api.Error, PlaceDetail] =
    place(id, asOf).map(d =>
      if withCounts then
        d.modify(_.counts)
          .setTo(Some(counts(now(id))))
          .into[PlaceDetail]
          .withFieldConst(_.media, pictures(d.place.id))
          .transform
      else d.into[PlaceDetail].withFieldConst(_.media, Nil).transform
    )
