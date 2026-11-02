package com.histmersion.api.catalogue
import com.histmersion.values.PlaceKind
import com.histmersion.api.given

/** Events follow CIDOC-CRM's four-point time-span: `notBefore`/`beginBy`/`endFrom`/`notAfter`, the outer span covering
  * every possible moment and the inner span only the certain core.
  */

object EventScale:
  val Local = "local"
  val Regional = "regional"

  val Auto = "auto"
  val all: List[String] = List(Local, Regional)

  val localKinds: Set[String] = Set(
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

  val regionalKinds: Set[String] = Set(
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

  def parse(s: String): Option[Option[String]] =
    s.trim.toLowerCase match
      case Local    => Some(Some(Local))
      case Regional => Some(Some(Regional))
      case Auto     => Some(None)
      case _        => None

  def normKind(kind: String): String = kind.trim.toLowerCase.replaceAll("[\\s-]+", "_")

  def ofKind(kind: String): Option[String] =
    val k = normKind(kind)
    if localKinds(k) then Some(Local)
    else if regionalKinds(k) || k.endsWith("_era") then Some(Regional)
    else None

  def ofPlaceKind(placeKind: String): Option[String] =
    placeKind.trim.toLowerCase match
      case PlaceKind.Site | PlaceKind.Feature                     => Some(Local)
      case PlaceKind.Region | PlaceKind.Polity | PlaceKind.People => Some(Regional)
      case _                                                      => None

  def ofGeometry(geometryType: String): Option[String] =
    geometryType.trim.toLowerCase.replace("st_", "") match
      case "point"                    => Some(Local)
      case "polygon" | "multipolygon" => Some(Regional)
      case _                          => None

  def effective(
      stated: Option[String],
      kind: String,
      hasParent: Boolean,
      placeKind: Option[String],
      geometryType: Option[String]
  ): String =
    stated
      .flatMap(s => parse(s).flatten)
      .orElse(ofKind(kind))
      .orElse(geometryType.flatMap(ofGeometry))
      .orElse(placeKind.flatMap(ofPlaceKind))
      .orElse(Option.when(hasParent)(Local))
      .getOrElse(Local)
