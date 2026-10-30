package com.histmersion.api

import sttp.tapir.Schema
import com.histmersion.values.{PlaceKind, WireEnumValues}

/** On what ground a place is in a TIME-FILTERED view (the map at a year, place search and suggestions with a time, a
  * place's children and subtree with a time). Without a time nothing is filtered.
  *
  * A view at a time shows what was there THEN (2026-10-09): a place valid "at every year" because its source states no
  * date is no evidence of the 3rd millennium BCE, and drawing the curated regions, the undated Pleiades sites and the
  * Natural Earth countries there showed less of what was there than of what came later. So:
  *   - [[Dated]]: its own period overlaps the window (an unknown start uses the stored window, V074).
  *   - [[Timeless]]: a physical feature is there at any time — a seed place of kind `feature` (Pleiades' rivers,
  *     mountains, islands, lakes, capes, passes, bays, springs; Natural Earth's seas, rivers, lakes, ranges, peaks; the
  *     curated valleys) or a Natural Earth physical REGION (`modern` and kind `region`: peninsulas, deserts, plains,
  *     basins). Never a GeoNames/Wikidata place.
  *   - [[Modern]]: a present-day place (`properties.modern`: the Natural Earth cities, every undated GeoNames/Wikidata
  *     place) — only when the view reaches `places.modern-from`.
  *   - [[Grouping]]: an undated node with no shape of its own (a continent, a Natural Earth subregion) — never drawn;
  *     kept in a tree so what lies under it stays reachable.
  *   - [[Findings]]: any other undated place — only when a finding or an event dated within the window is attached to
  *     it, and then drawn as "dated by its findings". A `people` (a tribe, placed at a representative point) is never
  *     timeless: dated by its period, else by its findings.
  * The map at a year also leaves out an undated polity known only by a point, findings or not
  * (`db.PlaceRepo.UndatedPolityPointSql`, 2026-10-09).
  */
enum PlaceTimeBasis:
  case Dated, Timeless, Modern, Grouping, Findings

  def wire: String = this match
    case Dated    => "dated"
    case Timeless => "timeless"
    case Modern   => "modern"
    case Grouping => "grouping"
    case Findings => "findings"

object PlaceTimeBasis:
  def parse(s: String): Option[PlaceTimeBasis] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  given WireEnumValues[PlaceTimeBasis]:
    def all: Array[PlaceTimeBasis] = PlaceTimeBasis.values
    def wire(e: PlaceTimeBasis): String = e.wire
    def fromWire(s: String): Option[PlaceTimeBasis] = parse(s)
  given io.circe.Encoder[PlaceTimeBasis] = WireEnum.encoder
  given io.circe.Decoder[PlaceTimeBasis] = WireEnum.decoder
  given Schema[PlaceTimeBasis] = WireEnum.schema

  /** The basis of one place. `undated` = its stored period is open on both sides (an unknown start is not: V074 stores
    * a window); `imported` = GeoNames or Wikidata. The SQL twin is `db.PlaceRepo.timeBasisSql`, and `PlaceTimeSuite`
    * holds the two to the same cases.
    */
  def of(kind: String, modern: Boolean, imported: Boolean, undated: Boolean, hasGeometry: Boolean): PlaceTimeBasis =
    if !imported && (kind == PlaceKind.Feature || (modern && kind == PlaceKind.Region)) then Timeless
    else if modern then Modern
    else if undated && !hasGeometry then Grouping
    else if undated then Findings
    else Dated

  /** Is a place of `basis` in a view whose window ends at `hi`? `overlaps` = its own period overlaps the window;
    * `evidence` = a finding or an event dated within the window is attached to it.
    */
  def visible(basis: PlaceTimeBasis, hi: Long, modernFrom: Long, overlaps: => Boolean, evidence: => Boolean): Boolean =
    basis match
      case Timeless | Grouping => true
      case Modern              => hi >= modernFrom
      case Dated               => overlaps
      case Findings            => evidence

  /** What a card says about a place a time view shows on the strength of its findings. */
  val FindingsNote: String = "dated by its findings — the place has no date of its own"
