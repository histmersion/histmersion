package com.histmersion.api.catalogue
import com.histmersion.api.given

/** Events follow CIDOC-CRM's four-point time-span: `notBefore`/`beginBy`/`endFrom`/`notAfter`, the outer span covering
  * every possible moment and the inner span only the certain core.
  */

object EventScale:
  type GeometryType = GeometryType.T
  object GeometryType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocalKinds = LocalKinds.T
  object LocalKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionalKinds = RegionalKinds.T
  object RegionalKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  export com.histmersion.api.catalogue.EventSummary.{Kind as OfKindKind, Kind as EffectiveKind}

  val Local = "local"
  val Regional = "regional"

  val Auto = "auto"
  val all: List[com.histmersion.api.catalogue.EventSummary.Scale] = List(_root_.com.histmersion.api.catalogue.EventSummary.Scale(Local), _root_.com.histmersion.api.catalogue.EventSummary.Scale(Regional))

  val localKinds: Set[com.histmersion.api.catalogue.EventScale.LocalKinds] = Set(
    _root_.com.histmersion.api.catalogue.EventScale.LocalKinds("battle"),
    _root_.com.histmersion.api.catalogue.EventScale.LocalKinds("naval_battle"),
    _root_.com.histmersion.api.catalogue.EventScale.LocalKinds("siege"),
    _root_.com.histmersion.api.catalogue.EventScale.LocalKinds("destruction"),
    _root_.com.histmersion.api.catalogue.EventScale.LocalKinds("construction"),
    _root_.com.histmersion.api.catalogue.EventScale.LocalKinds("founding"),
    _root_.com.histmersion.api.catalogue.EventScale.LocalKinds("eruption"),
    _root_.com.histmersion.api.catalogue.EventScale.LocalKinds("earthquake"),
    _root_.com.histmersion.api.catalogue.EventScale.LocalKinds("tsunami"),
    _root_.com.histmersion.api.catalogue.EventScale.LocalKinds("flood")
  )

  val regionalKinds: Set[com.histmersion.api.catalogue.EventScale.RegionalKinds] = Set(
    _root_.com.histmersion.api.catalogue.EventScale.RegionalKinds("migration"),
    _root_.com.histmersion.api.catalogue.EventScale.RegionalKinds("technological_change"),
    _root_.com.histmersion.api.catalogue.EventScale.RegionalKinds("cultural_shift"),
    _root_.com.histmersion.api.catalogue.EventScale.RegionalKinds("era"),
    _root_.com.histmersion.api.catalogue.EventScale.RegionalKinds("period"),
    _root_.com.histmersion.api.catalogue.EventScale.RegionalKinds("reign"),
    _root_.com.histmersion.api.catalogue.EventScale.RegionalKinds("war"),
    _root_.com.histmersion.api.catalogue.EventScale.RegionalKinds("invasion"),
    _root_.com.histmersion.api.catalogue.EventScale.RegionalKinds("collapse"),
    _root_.com.histmersion.api.catalogue.EventScale.RegionalKinds("polity_formation"),
    _root_.com.histmersion.api.catalogue.EventScale.RegionalKinds("drought"),
    _root_.com.histmersion.api.catalogue.EventScale.RegionalKinds("climate_event"),
    _root_.com.histmersion.api.catalogue.EventScale.RegionalKinds("epidemic"),
    _root_.com.histmersion.api.catalogue.EventScale.RegionalKinds("famine")
  )

  def parse(s: com.histmersion.api.catalogue.EventSummary.Scale): Option[Option[com.histmersion.api.catalogue.EventSummary.Scale]] =
    (s).value.trim.toLowerCase match
      case Local    => Some(Some(_root_.com.histmersion.api.catalogue.EventSummary.Scale(Local)))
      case Regional => Some(Some(_root_.com.histmersion.api.catalogue.EventSummary.Scale(Regional)))
      case Auto     => Some(None)
      case _        => None

  def normKind(kind: com.histmersion.api.catalogue.EventSummary.Kind): com.histmersion.api.catalogue.EventSummary.Kind = _root_.com.histmersion.api.catalogue.EventSummary.Kind((kind).value.trim.toLowerCase.replaceAll("[\\s-]+", "_"))

  def ofKind(kind: com.histmersion.api.catalogue.EventScale.OfKindKind): Option[com.histmersion.api.catalogue.EventSummary.Scale] =
    val k = normKind(kind)
    if localKinds(_root_.com.histmersion.api.catalogue.EventScale.LocalKinds((k).value)) then Some(_root_.com.histmersion.api.catalogue.EventSummary.Scale(Local))
    else if regionalKinds(_root_.com.histmersion.api.catalogue.EventScale.RegionalKinds((k).value)) || (k).value.endsWith("_era") then Some(_root_.com.histmersion.api.catalogue.EventSummary.Scale(Regional))
    else None

  def ofPlaceKind(placeKind: com.histmersion.api.catalogue.Place.Kind.Wire): Option[com.histmersion.api.catalogue.EventSummary.Scale] =
    (placeKind).value.trim.toLowerCase match
      case com.histmersion.api.catalogue.Place.Kind.Site | com.histmersion.api.catalogue.Place.Kind.Feature =>
        Some(_root_.com.histmersion.api.catalogue.EventSummary.Scale(Local))
      case com.histmersion.api.catalogue.Place.Kind.Region | com.histmersion.api.catalogue.Place.Kind.Polity |
          com.histmersion.api.catalogue.Place.Kind.People =>
        Some(_root_.com.histmersion.api.catalogue.EventSummary.Scale(Regional))
      case _ => None

  def ofGeometry(geometryType: com.histmersion.api.catalogue.EventScale.GeometryType): Option[com.histmersion.api.catalogue.EventSummary.Scale] =
    (geometryType).value.trim.toLowerCase.replace("st_", "") match
      case "point"                    => Some(_root_.com.histmersion.api.catalogue.EventSummary.Scale(Local))
      case "polygon" | "multipolygon" => Some(_root_.com.histmersion.api.catalogue.EventSummary.Scale(Regional))
      case _                          => None

  def effective(
      stated: Option[com.histmersion.api.catalogue.EventSummary.Scale],
      kind: com.histmersion.api.catalogue.EventScale.EffectiveKind,
      hasParent: Boolean,
      placeKind: Option[com.histmersion.api.catalogue.Place.Kind.Wire],
      geometryType: Option[com.histmersion.api.catalogue.EventScale.GeometryType]
  ): com.histmersion.api.catalogue.EventSummary.Scale =
    stated
      .flatMap(s => parse(s).flatten)
      .orElse(ofKind(kind))
      .orElse(geometryType.flatMap(ofGeometry))
      .orElse(placeKind.flatMap(ofPlaceKind))
      .orElse(Option.when(hasParent)(_root_.com.histmersion.api.catalogue.EventSummary.Scale(Local)))
      .getOrElse(_root_.com.histmersion.api.catalogue.EventSummary.Scale(Local))
