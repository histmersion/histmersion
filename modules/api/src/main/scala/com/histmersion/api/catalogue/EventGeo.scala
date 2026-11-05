package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.TimeSpan
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class EventGeo(
    id: com.histmersion.api.catalogue.Event.Id,
    label: EventGeo.Label,
    kind: EventGeo.Kind,
    scale: EventGeo.Scale,
    historicity: EventGeo.Historicity,
    timeSpan: TimeSpan,
    lat: com.histmersion.api.catalogue.Latitude,
    lon: com.histmersion.api.catalogue.Longitude,
    anchor: EventGeo.Anchor,
    placeId: Option[com.histmersion.api.catalogue.Place.Id] = None,
    placeLabel: Option[EventGeo.PlaceLabel] = None,
    parentId: Option[EventGeo.ParentId] = None,
    outline: Option[EventGeo.Outline] = None,
    tier: EventGeo.Tier = EventTier.Attested,
    size: Option[EventGeo.Size] = None,
    precision: EventGeo.Precision = EventPrecision.Precise,
    dating: Option[EventGeo.Dating] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object EventGeo:


  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Scale = Scale.T
  object Scale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Historicity = Historicity.T
  object Historicity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Anchor = Anchor.T
  object Anchor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceLabel = PlaceLabel.T
  object PlaceLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParentId = ParentId.T
  object ParentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Outline = Outline.T
  object Outline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tier = Tier.T
  object Tier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Size = Size.T
  object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Precision = Precision.T
  object Precision extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Dating = Dating.T
  object Dating extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def missingEnd(ts: TimeSpan): Option[String] =
    val hasStart = ts.notBefore.isDefined || ts.beginBy.isDefined
    val hasEnd = ts.endFrom.isDefined || ts.notAfter.isDefined
    if hasStart && !hasEnd then Some("end not recorded")
    else if hasEnd && !hasStart then Some("start not recorded")
    else None

  final case class List(events: scala.List[EventGeo], unplaced: List.Unplaced = 0, imprecise: List.Imprecise = 0)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object List:
    type Unplaced = Unplaced.T
    object Unplaced extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Imprecise = Imprecise.T
    object Imprecise extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
