package com.histmersion
package api.catalogue
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** Events follow CIDOC-CRM's four-point time-span: `notBefore`/`beginBy`/`endFrom`/`notAfter`, the outer span covering
  * every possible moment and the inner span only the certain core.
  */

@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object EventScale:
  type GeometryType = GeometryType.T
  object GeometryType extends Newtype[String, Pure]
  type LocalKinds = LocalKinds.T
  object LocalKinds extends Newtype[String, Pure]
  type RegionalKinds = RegionalKinds.T
  object RegionalKinds extends Newtype[String, Pure]
  export EventSummary.{Kind as EffectiveKind, Kind as OfKindKind}

  val Local = "local"
  val Regional = "regional"

  val Auto = "auto"
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: EventSummary.Scale): Option[Option[EventSummary.Scale]] =
    (s).value.trim.toLowerCase match
      case Local    => Some(Some(EventSummary.Scale(Local)))
      case Regional => Some(Some(EventSummary.Scale(Regional)))
      case Auto     => Some(None)
      case _        => None

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def normKind(kind: EventSummary.Kind): EventSummary.Kind =
    EventSummary.Kind((kind).value.trim.toLowerCase.replaceAll("[\\s-]+", "_"))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def ofKind(kind: EventScale.OfKindKind): Option[EventSummary.Scale] =
    val k = normKind(kind)
    if localKinds(LocalKinds((k).value)) then Some(EventSummary.Scale(Local))
    else if regionalKinds(RegionalKinds((k).value)) || (k).value.endsWith("_era") then
      Some(EventSummary.Scale(Regional))
    else None

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def ofPlaceKind(placeKind: Place.Kind.Wire): Option[EventSummary.Scale] =
    (placeKind).value.trim.toLowerCase match
      case Place.Kind.Site | Place.Kind.Feature =>
        Some(EventSummary.Scale(Local))
      case Place.Kind.Region | Place.Kind.Polity | Place.Kind.People =>
        Some(EventSummary.Scale(Regional))
      case _ => None

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def ofGeometry(geometryType: GeometryType): Option[EventSummary.Scale] =
    (geometryType).value.trim.toLowerCase.replace("st_", "") match
      case "point"                    => Some(EventSummary.Scale(Local))
      case "polygon" | "multipolygon" => Some(EventSummary.Scale(Regional))
      case _                          => None

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
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
