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
  type OfKindKind = _root_.com.histmersion.api.catalogue.EventScale.NormKindKind
  val OfKindKind: _root_.com.histmersion.api.catalogue.EventScale.NormKindKind.type = _root_.com.histmersion.api.catalogue.EventScale.NormKindKind
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
  type EffectiveKind = _root_.com.histmersion.api.catalogue.EventScale.NormKindKind
  val EffectiveKind: _root_.com.histmersion.api.catalogue.EventScale.NormKindKind.type = _root_.com.histmersion.api.catalogue.EventScale.NormKindKind
  type EffectiveHasParent = EffectiveHasParent.T
  object EffectiveHasParent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EffectivePlaceKind = EffectivePlaceKind.T
  object EffectivePlaceKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EffectiveGeometryType = EffectiveGeometryType.T
  object EffectiveGeometryType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Local = "local"
  val Regional = "regional"

  val Auto = "auto"
  val all: List[com.histmersion.api.catalogue.EventScale.All] = List(_root_.com.histmersion.api.catalogue.EventScale.All(Local), _root_.com.histmersion.api.catalogue.EventScale.All(Regional))

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

  def parse(s: com.histmersion.api.catalogue.EventScale.ParseS): Option[Option[com.histmersion.api.catalogue.EventScale.ParseResult]] =
    (s).value.trim.toLowerCase match
      case Local    => Some(Some(_root_.com.histmersion.api.catalogue.EventScale.ParseResult(Local)))
      case Regional => Some(Some(_root_.com.histmersion.api.catalogue.EventScale.ParseResult(Regional)))
      case Auto     => Some(None)
      case _        => None

  def normKind(kind: com.histmersion.api.catalogue.EventScale.NormKindKind): com.histmersion.api.catalogue.EventScale.NormKindResult = _root_.com.histmersion.api.catalogue.EventScale.NormKindResult((kind).value.trim.toLowerCase.replaceAll("[\\s-]+", "_"))

  def ofKind(kind: com.histmersion.api.catalogue.EventScale.OfKindKind): Option[com.histmersion.api.catalogue.EventScale.OfKindResult] =
    val k = normKind(kind)
    if localKinds(_root_.com.histmersion.api.catalogue.EventScale.LocalKinds((k).value)) then Some(_root_.com.histmersion.api.catalogue.EventScale.OfKindResult(Local))
    else if regionalKinds(_root_.com.histmersion.api.catalogue.EventScale.RegionalKinds((k).value)) || (k).value.endsWith("_era") then Some(_root_.com.histmersion.api.catalogue.EventScale.OfKindResult(Regional))
    else None

  def ofPlaceKind(placeKind: com.histmersion.api.catalogue.EventScale.OfPlaceKindPlaceKind): Option[com.histmersion.api.catalogue.EventScale.OfPlaceKindResult] =
    (placeKind).value.trim.toLowerCase match
      case com.histmersion.api.catalogue.Place.Kind.Site | com.histmersion.api.catalogue.Place.Kind.Feature =>
        Some(_root_.com.histmersion.api.catalogue.EventScale.OfPlaceKindResult(Local))
      case com.histmersion.api.catalogue.Place.Kind.Region | com.histmersion.api.catalogue.Place.Kind.Polity |
          com.histmersion.api.catalogue.Place.Kind.People =>
        Some(_root_.com.histmersion.api.catalogue.EventScale.OfPlaceKindResult(Regional))
      case _ => None

  def ofGeometry(geometryType: com.histmersion.api.catalogue.EventScale.OfGeometryGeometryType): Option[com.histmersion.api.catalogue.EventScale.OfGeometryResult] =
    (geometryType).value.trim.toLowerCase.replace("st_", "") match
      case "point"                    => Some(_root_.com.histmersion.api.catalogue.EventScale.OfGeometryResult(Local))
      case "polygon" | "multipolygon" => Some(_root_.com.histmersion.api.catalogue.EventScale.OfGeometryResult(Regional))
      case _                          => None

  def effective(
      stated: Option[com.histmersion.api.catalogue.EventScale.EffectiveStated],
      kind: com.histmersion.api.catalogue.EventScale.EffectiveKind,
      hasParent: com.histmersion.api.catalogue.EventScale.EffectiveHasParent,
      placeKind: Option[com.histmersion.api.catalogue.EventScale.EffectivePlaceKind],
      geometryType: Option[com.histmersion.api.catalogue.EventScale.EffectiveGeometryType]
  ): com.histmersion.api.catalogue.EventScale.EffectiveResult =
    stated
      .flatMap(s => parse(_root_.com.histmersion.api.catalogue.EventScale.ParseS((s).value)).flatten)
      .orElse(ofKind(kind))
      .orElse(geometryType.flatMap({ val refactorFunction2 = ofGeometry; (refactorValue2) => refactorFunction2(_root_.com.histmersion.api.catalogue.EventScale.OfGeometryGeometryType((refactorValue2).value)) }))
      .orElse(placeKind.flatMap({ val refactorFunction3 = ofPlaceKind; (refactorValue3) => refactorFunction3(_root_.com.histmersion.api.catalogue.EventScale.OfPlaceKindPlaceKind((refactorValue3).value)) }))
      .orElse(Option.when(((hasParent) == _root_.com.histmersion.api.catalogue.EventScale.EffectiveHasParent(true)))(Local))
      .getOrElse(Local)
