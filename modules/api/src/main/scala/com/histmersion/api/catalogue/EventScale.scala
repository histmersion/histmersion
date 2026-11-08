package com.histmersion
package api.catalogue
import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Events follow CIDOC-CRM's four-point time-span: `notBefore`/`beginBy`/`endFrom`/`notAfter`, the outer span covering
  * every possible moment and the inner span only the certain core.
  */

object EventScale:
  type GeometryType = GeometryType.T
  object GeometryType extends RefinedType[String, Pure]
  type LocalKinds = LocalKinds.T
  object LocalKinds extends RefinedType[String, Pure]
  type RegionalKinds = RegionalKinds.T
  object RegionalKinds extends RefinedType[String, Pure]
  export EventSummary.{Kind as OfKindKind, Kind as EffectiveKind}

  val Local = "local"
  val Regional = "regional"

  val Auto = "auto"
  val all: List[EventSummary.Scale] = List(EventSummary.Scale(Local), EventSummary.Scale(Regional))

  val localKinds: Set[LocalKinds] = Set(
    LocalKinds("battle"),
    LocalKinds("naval_battle"),
    LocalKinds("siege"),
    LocalKinds("destruction"),
    LocalKinds("construction"),
    LocalKinds("founding"),
    LocalKinds("eruption"),
    LocalKinds("earthquake"),
    LocalKinds("tsunami"),
    LocalKinds("flood")
  )

  val regionalKinds: Set[RegionalKinds] = Set(
    RegionalKinds("migration"),
    RegionalKinds("technological_change"),
    RegionalKinds("cultural_shift"),
    RegionalKinds("era"),
    RegionalKinds("period"),
    RegionalKinds("reign"),
    RegionalKinds("war"),
    RegionalKinds("invasion"),
    RegionalKinds("collapse"),
    RegionalKinds("polity_formation"),
    RegionalKinds("drought"),
    RegionalKinds("climate_event"),
    RegionalKinds("epidemic"),
    RegionalKinds("famine")
  )

  def parse(s: EventSummary.Scale): Option[Option[EventSummary.Scale]] =
    (s).value.trim.toLowerCase match
      case Local    => Some(Some(EventSummary.Scale(Local)))
      case Regional => Some(Some(EventSummary.Scale(Regional)))
      case Auto     => Some(None)
      case _        => None

  def normKind(kind: EventSummary.Kind): EventSummary.Kind = EventSummary.Kind((kind).value.trim.toLowerCase.replaceAll("[\\s-]+", "_"))

  def ofKind(kind: EventScale.OfKindKind): Option[EventSummary.Scale] =
    val k = normKind(kind)
    if localKinds(LocalKinds((k).value)) then Some(EventSummary.Scale(Local))
    else if regionalKinds(RegionalKinds((k).value)) || (k).value.endsWith("_era") then Some(EventSummary.Scale(Regional))
    else None

  def ofPlaceKind(placeKind: Place.Kind.Wire): Option[EventSummary.Scale] =
    (placeKind).value.trim.toLowerCase match
      case Place.Kind.Site | Place.Kind.Feature =>
        Some(EventSummary.Scale(Local))
      case Place.Kind.Region | Place.Kind.Polity |
          Place.Kind.People =>
        Some(EventSummary.Scale(Regional))
      case _ => None

  def ofGeometry(geometryType: GeometryType): Option[EventSummary.Scale] =
    (geometryType).value.trim.toLowerCase.replace("st_", "") match
      case "point"                    => Some(EventSummary.Scale(Local))
      case "polygon" | "multipolygon" => Some(EventSummary.Scale(Regional))
      case _                          => None

  def effective(
      stated: Option[EventSummary.Scale],
      kind: EventScale.EffectiveKind,
      hasParent: Boolean,
      placeKind: Option[Place.Kind.Wire],
      geometryType: Option[GeometryType]
  ): EventSummary.Scale =
    stated
      .flatMap(s => parse(s).flatten)
      .orElse(ofKind(kind))
      .orElse(geometryType.flatMap(ofGeometry))
      .orElse(placeKind.flatMap(ofPlaceKind))
      .orElse(Option.when(hasParent)(EventSummary.Scale(Local)))
      .getOrElse(EventSummary.Scale(Local))
