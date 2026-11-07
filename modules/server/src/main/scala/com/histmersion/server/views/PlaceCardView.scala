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
    place: (PlaceCardView.PlaceArg1, Option[PlaceCardView.PlaceArg2]) => Either[Api.Error, Place.Entry],
    now: PlaceCardView.NowArg1 => PlaceCardView.NowResult,
    counts: PlaceCardView.CountsArg1 => Place.Counts,
    pictures: PlaceCardView.PicturesArg1 => List[MediaThumb]
):
  def apply(id: com.histmersion.domain.catalogue.Place.Id, asOf: Option[com.histmersion.server.views.PlaceCardView.AsOf], withCounts: com.histmersion.server.views.PlaceCardView.WithCounts): Either[Api.Error, PlaceDetail] =
    place(id, asOf).map(d =>
      if withCounts then
        d.modify(_.counts)
          .setTo(Some(counts(now(id))))
          .into[PlaceDetail]
          .withFieldConst(_.media, pictures(d.place.id))
          .transform
      else d.into[PlaceDetail].withFieldConst(_.media, Nil).transform
    )

object PlaceCardView:
  type AsOf = AsOf.T
  object AsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceName = PlaceName.T
  object PlaceName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithCounts = WithCounts.T
  object WithCounts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceArg1 = com.histmersion.domain.catalogue.Place.Id
  val PlaceArg1: _root_.com.histmersion.domain.catalogue.Place.Id.type = _root_.com.histmersion.domain.catalogue.Place.Id
  type PlaceArg2 = com.histmersion.server.views.PlaceCardView.AsOf
  val PlaceArg2: _root_.com.histmersion.server.views.PlaceCardView.AsOf.type = _root_.com.histmersion.server.views.PlaceCardView.AsOf
  type NowArg1 = com.histmersion.domain.catalogue.Place.Id
  val NowArg1: _root_.com.histmersion.domain.catalogue.Place.Id.type = _root_.com.histmersion.domain.catalogue.Place.Id
  type NowResult = com.histmersion.server.views.PlaceCardView.PlaceName
  val NowResult: _root_.com.histmersion.server.views.PlaceCardView.PlaceName.type = _root_.com.histmersion.server.views.PlaceCardView.PlaceName
  type CountsArg1 = com.histmersion.domain.catalogue.Place.Id
  val CountsArg1: _root_.com.histmersion.domain.catalogue.Place.Id.type = _root_.com.histmersion.domain.catalogue.Place.Id
  type PicturesArg1 = com.histmersion.domain.catalogue.Place.Id
  val PicturesArg1: _root_.com.histmersion.domain.catalogue.Place.Id.type = _root_.com.histmersion.domain.catalogue.Place.Id

