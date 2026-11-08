package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.TimeSpan
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
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
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object EventGeo:
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]



  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Scale = Scale.T
  object Scale extends RefinedType[String, Pure]
  type Historicity = Historicity.T
  object Historicity extends RefinedType[String, Pure]
  type Anchor = Anchor.T
  object Anchor extends RefinedType[String, Pure]
  type PlaceLabel = PlaceLabel.T
  object PlaceLabel extends RefinedType[String, Pure]
  type ParentId = ParentId.T
  object ParentId extends RefinedType[String, Pure]
  type Outline = Outline.T
  object Outline extends RefinedType[String, Pure]
  type Tier = Tier.T
  object Tier extends RefinedType[String, Pure]
  type Size = Size.T
  object Size extends RefinedType[String, Pure]
  type Precision = Precision.T
  object Precision extends RefinedType[String, Pure]
  type Dating = Dating.T
  object Dating extends RefinedType[String, Pure]:
    type Description = Description.T
    object Description extends RefinedType[String, Pure]

  def missingEnd(ts: TimeSpan): Option[Problem] =
    val hasStart = ts.notBefore.isDefined || ts.beginBy.isDefined
    val hasEnd = ts.endFrom.isDefined || ts.notAfter.isDefined
    if hasStart && !hasEnd then Some(Problem("end not recorded"))
    else if hasEnd && !hasStart then Some(Problem("start not recorded"))
    else None

  final case class List(events: scala.List[EventGeo], unplaced: List.Unplaced = List.Unplaced(0), imprecise: List.Imprecise = List.Imprecise(0))
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object List:
    type Unplaced = Unplaced.T
    object Unplaced extends RefinedType[Int, Pure]
    type Imprecise = Imprecise.T
    object Imprecise extends RefinedType[Int, Pure]
