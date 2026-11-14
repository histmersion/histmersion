package com.histmersion
package api.catalogue

import com.histmersion.util.problem.Problem

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.TimeSpan
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class EventGeo(
    id: Event.Id,
    label: EventGeo.Label,
    kind: EventGeo.Kind,
    scale: EventGeo.Scale,
    historicity: EventGeo.Historicity,
    timeSpan: TimeSpan,
    lat: Latitude,
    lon: Longitude,
    anchor: EventGeo.Anchor,
    placeId: Option[Place.Id] = None,
    placeLabel: Option[EventGeo.PlaceLabel] = None,
    parentId: Option[EventGeo.ParentId] = None,
    outline: Option[EventGeo.Outline] = None,
    tier: EventGeo.Tier = EventGeo.Tier(EventTier.Attested),
    size: Option[EventGeo.Size] = None,
    precision: EventGeo.Precision = EventGeo.Precision(EventPrecision.Precise),
    dating: Option[EventGeo.Dating] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema
object EventGeo:

  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Scale = Scale.T
  object Scale extends Newtype[String, Pure]
  type Historicity = Historicity.T
  object Historicity extends Newtype[String, Pure]
  type Anchor = Anchor.T
  object Anchor extends Newtype[String, Pure]
  type PlaceLabel = PlaceLabel.T
  object PlaceLabel extends Newtype[String, Pure]
  type ParentId = ParentId.T
  object ParentId extends Newtype[String, Pure]
  type Outline = Outline.T
  object Outline extends Newtype[String, Pure]
  type Tier = Tier.T
  object Tier extends Newtype[String, Pure]
  type Size = Size.T
  object Size extends Newtype[String, Pure]
  type Precision = Precision.T
  object Precision extends Newtype[String, Pure]
  type Dating = Dating.T
  object Dating extends Newtype[String, Pure]:
    type Description = Description.T
    object Description extends Newtype[String, Pure]

  def missingEnd(ts: TimeSpan): Option[Problem] =
    val hasStart = ts.notBefore.isDefined || ts.beginBy.isDefined
    val hasEnd = ts.endFrom.isDefined || ts.notAfter.isDefined
    if hasStart && !hasEnd then Some(Problem.Invalid.Missing.here(Problem.Field("end")))
    else if hasEnd && !hasStart then Some(Problem.Invalid.Missing.here(Problem.Field("start")))
    else None

  final case class List(
      events: scala.List[EventGeo],
      unplaced: List.Unplaced = List.Unplaced(0),
      imprecise: List.Imprecise = List.Imprecise(0)
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object List:
    type Unplaced = Unplaced.T
    object Unplaced extends Newtype[Int, Pure]
    type Imprecise = Imprecise.T
    object Imprecise extends Newtype[Int, Pure]
