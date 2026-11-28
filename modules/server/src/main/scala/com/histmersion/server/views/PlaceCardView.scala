package com.histmersion
package server.views

import com.histmersion.api.media.MediaThumb
import com.histmersion.api.views.PlaceDetail
import hearth.kindlings.optics.syntax.*
import io.scalaland.chimney.dsl.*
import com.histmersion.api.Api
import com.histmersion.domain.DeepTime
import com.histmersion.api.catalogue.Place
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

/** A place's page as the gazetteer, the corpus and the pictures show it together (the web UI's map hover card): the
  * gazetteer's `place` (the row, its geometry and the hierarchy around it), and — with `counts` — what `place=<id>`
  * selects in each list (`counts` of the place's id now) and the place's pictures.
  */
final class PlaceCardView(
    place: (PlaceCardView.PlaceId, Option[DeepTime.SignedYear]) => Either[Api.Error, Place.Entry],
    now: PlaceCardView.PlaceId => PlaceCardView.PlaceId,
    counts: PlaceCardView.PlaceId => Place.Counts,
    pictures: PlaceCardView.PlaceId => List[MediaThumb]
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(
      id: PlaceCardView.PlaceId,
      asOf: Option[DeepTime.SignedYear],
      withCounts: PlaceCardView.WithCounts
  ): Either[Api.Error, PlaceDetail] =
    place(id, asOf).map(d =>
      if withCounts.withCounts then
        d.modify(_.counts)
          .setTo(Some(counts(now(id))))
          .into[PlaceDetail]
          .withFieldConst(_.media, pictures(PlaceCardView.PlaceId.assume(d.place.id.value)))
          .transform
      else d.into[PlaceDetail].withFieldConst(_.media, Nil).transform
    )

object PlaceCardView:
  export domain.catalogue.Place.Id as PlaceId
  enum WithCounts derives CanEqual:
    case WithCounts, WithoutCounts
  object WithCounts extends TwoCases[WithCounts]:
    extension (withCounts: WithCounts) def withCounts: Boolean = withCounts == WithCounts
