package com.histmersion.api.catalogue
import com.histmersion.api.given

/** Events follow CIDOC-CRM's four-point time-span: `notBefore`/`beginBy`/`endFrom`/`notAfter`, the outer span covering
  * every possible moment and the inner span only the certain core.
  */

object EventScale:
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocalKinds = LocalKinds.T
  object LocalKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionalKinds = RegionalKinds.T
  object RegionalKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResult = ParseResult.T
  object ParseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NormKindResult = NormKindResult.T
  object NormKindResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NormKindKind = NormKindKind.T
  object NormKindKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfKindResult = OfKindResult.T
  object OfKindResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfKindKind = OfKindKind.T
  object OfKindKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfPlaceKindResult = OfPlaceKindResult.T
  object OfPlaceKindResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfPlaceKindPlaceKind = OfPlaceKindPlaceKind.T
  object OfPlaceKindPlaceKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfGeometryResult = OfGeometryResult.T
  object OfGeometryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfGeometryGeometryType = OfGeometryGeometryType.T
  object OfGeometryGeometryType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EffectiveResult = EffectiveResult.T
  object EffectiveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EffectiveStated = EffectiveStated.T
  object EffectiveStated extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EffectiveKind = EffectiveKind.T
  object EffectiveKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EffectiveHasParent = EffectiveHasParent.T
  object EffectiveHasParent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EffectivePlaceKind = EffectivePlaceKind.T
  object EffectivePlaceKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EffectiveGeometryType = EffectiveGeometryType.T
  object EffectiveGeometryType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Local = "local"
  val Regional = "regional"

  val Auto = "auto"
  val all: List[com.histmersion.api.catalogue.EventScale.All] = List(Local, Regional)

  val localKinds: Set[com.histmersion.api.catalogue.EventScale.LocalKinds] = Set(
    "battle",
    "naval_battle",
    "siege",
    "destruction",
    "construction",
    "founding",
    "eruption",
    "earthquake",
    "tsunami",
    "flood"
  )

  val regionalKinds: Set[com.histmersion.api.catalogue.EventScale.RegionalKinds] = Set(
    "migration",
    "technological_change",
    "cultural_shift",
    "era",
    "period",
    "reign",
    "war",
    "invasion",
    "collapse",
    "polity_formation",
    "drought",
    "climate_event",
    "epidemic",
    "famine"
  )

  def parse(s: com.histmersion.api.catalogue.EventScale.ParseS): Option[Option[com.histmersion.api.catalogue.EventScale.ParseResult]] =
    s.trim.toLowerCase match
      case Local    => Some(Some(Local))
      case Regional => Some(Some(Regional))
      case Auto     => Some(None)
      case _        => None

  def normKind(kind: com.histmersion.api.catalogue.EventScale.NormKindKind): com.histmersion.api.catalogue.EventScale.NormKindResult = kind.trim.toLowerCase.replaceAll("[\\s-]+", "_")

  def ofKind(kind: com.histmersion.api.catalogue.EventScale.OfKindKind): Option[com.histmersion.api.catalogue.EventScale.OfKindResult] =
    val k = normKind(kind)
    if localKinds(k) then Some(Local)
    else if regionalKinds(k) || k.endsWith("_era") then Some(Regional)
    else None

  def ofPlaceKind(placeKind: com.histmersion.api.catalogue.EventScale.OfPlaceKindPlaceKind): Option[com.histmersion.api.catalogue.EventScale.OfPlaceKindResult] =
    placeKind.trim.toLowerCase match
      case com.histmersion.api.catalogue.Place.Kind.Site | com.histmersion.api.catalogue.Place.Kind.Feature =>
        Some(Local)
      case com.histmersion.api.catalogue.Place.Kind.Region | com.histmersion.api.catalogue.Place.Kind.Polity |
          com.histmersion.api.catalogue.Place.Kind.People =>
        Some(Regional)
      case _ => None

  def ofGeometry(geometryType: com.histmersion.api.catalogue.EventScale.OfGeometryGeometryType): Option[com.histmersion.api.catalogue.EventScale.OfGeometryResult] =
    geometryType.trim.toLowerCase.replace("st_", "") match
      case "point"                    => Some(Local)
      case "polygon" | "multipolygon" => Some(Regional)
      case _                          => None

  def effective(
      stated: Option[com.histmersion.api.catalogue.EventScale.EffectiveStated],
      kind: com.histmersion.api.catalogue.EventScale.EffectiveKind,
      hasParent: com.histmersion.api.catalogue.EventScale.EffectiveHasParent,
      placeKind: Option[com.histmersion.api.catalogue.EventScale.EffectivePlaceKind],
      geometryType: Option[com.histmersion.api.catalogue.EventScale.EffectiveGeometryType]
  ): com.histmersion.api.catalogue.EventScale.EffectiveResult =
    stated
      .flatMap(s => parse(s).flatten)
      .orElse(ofKind(kind))
      .orElse(geometryType.flatMap(ofGeometry))
      .orElse(placeKind.flatMap(ofPlaceKind))
      .orElse(Option.when(hasParent)(Local))
      .getOrElse(Local)
