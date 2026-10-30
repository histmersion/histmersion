package com.histmersion.api

import com.histmersion.values.PlaceKind

/** The EVENTS database (plan §1.2): CIDOC-CRM compatible events with four-point time-spans, hierarchical composition,
  * causal/temporal edges and participant roles.
  *
  * A four-point time-span records both the certain and the uncertain bounds of an event's temporal extent: `notBefore`
  * / `beginBy` / `endFrom` / `notAfter`. The outer span (notBefore..notAfter) covers every possible moment; the inner
  * span (beginBy..endFrom) covers only the certain core. Either bound may be absent (unknown).
  */

// ── vocabulary ─────────────────────────────────────────────────────────────────────────────────

/** An event's SCALE (#1440): LOCAL — it happened at one place (a battle, a siege, a destruction); or REGIONAL — it
  * happened across a region (a migration, a technological change, an era, a war, whose battles are its local
  * sub-events). The map's playback draws a local event as a point and label at its site, fading after it ends, and a
  * regional one as a label at the region's centre with the region's outline for its span.
  *
  * The stored value is optional; [[effective]] derives the scale of an event that states none.
  */
object EventScale:
  val Local = "local"
  val Regional = "regional"

  /** On a patch: forget the stated scale, derive it again. Never stored. */
  val Auto = "auto"
  val all: List[String] = List(Local, Regional)

  /** Kinds that happen at ONE place. */
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

  /** Kinds that happen across a region (`*_era` too: `diplomatic_era`). */
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

  /** Pure: `local`/`regional` as stored, `auto` as `Some(None)` (clear), anything else `None`. */
  def parse(s: String): Option[Option[String]] =
    s.trim.toLowerCase match
      case Local    => Some(Some(Local))
      case Regional => Some(Some(Regional))
      case Auto     => Some(None)
      case _        => None

  /** Pure: the normalised kind (`Technological change` → `technological_change`). */
  def normKind(kind: String): String = kind.trim.toLowerCase.replaceAll("[\\s-]+", "_")

  /** Pure: the scale an event's KIND implies, if it implies one. */
  def ofKind(kind: String): Option[String] =
    val k = normKind(kind)
    if localKinds(k) then Some(Local)
    else if regionalKinds(k) || k.endsWith("_era") then Some(Regional)
    else None

  /** Pure: the scale a PLACE's kind implies — a site or feature is a place where something happens, a region, polity or
    * people an area.
    */
  def ofPlaceKind(placeKind: String): Option[String] =
    placeKind.trim.toLowerCase match
      case PlaceKind.Site | PlaceKind.Feature                     => Some(Local)
      case PlaceKind.Region | PlaceKind.Polity | PlaceKind.People => Some(Regional)
      case _                                                      => None

  /** Pure: what an event's own geometry implies (a GeoJSON or PostGIS geometry type): a point is a place, a polygon an
    * area.
    */
  def ofGeometry(geometryType: String): Option[String] =
    geometryType.trim.toLowerCase.replace("st_", "") match
      case "point"                    => Some(Local)
      case "polygon" | "multipolygon" => Some(Regional)
      case _                          => None

  /** Pure: the scale an event has. In order: the STATED one; its kind's (a war is regional, its battles local); its own
    * geometry's (what the map draws it at, when it has one); its place's kind's; `local` for a sub-event that says
    * nothing else (a part of a larger event); else `local`.
    */
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
