package com.histmersion.api

import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

/** How much of a place's period its source STATES.
  *
  * The bundled seed and the curated places state their periods on purpose — an open side is open (a modern country
  * "until today", a timeless region): [[Stated]]. The bulk external sources often say less (2026-10-09): a Wikidata
  * item with a dissolution date and no inception does not mean the place existed "since forever", and a GeoNames
  * village with no date is not valid in 3000 BCE. So:
  *   - [[EndOnly]]: only the end is known. The start is UNKNOWN; every time filter (the map at a year, search or place
  *     picking with a time, the geocoder, `finding_places`) sees the place only within a window of
  *     `places.sources.open-start-window-years` before its end — `places.period` holds that window and
  *     `places.start_unknown` says so (V074).
  *   - [[Unknown]]: no date at all (a GeoNames or Wikidata place with neither). Kept for geocoding and picking by name,
  *     ranked below dated candidates, never drawn on a time-sliced map.
  * Only `wikidata` and `geonames` places can be other than [[Stated]]; an LPF dataset's places follow the dataset's own
  * `when` (a feature's, else the dataset's).
  */
enum PlaceDating:
  case Stated, EndOnly, Unknown

  def wire: String = this match
    case Stated  => "stated"
    case EndOnly => "end-only"
    case Unknown => "unknown"

object PlaceDating:
  def parse(s: String): Option[PlaceDating] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  given WireEnumValues[PlaceDating]:
    def all: Array[PlaceDating] = PlaceDating.values
    def wire(e: PlaceDating): String = e.wire
    def fromWire(s: String): Option[PlaceDating] = parse(s)
  given io.circe.Encoder[PlaceDating] = WireEnum.encoder
  given io.circe.Decoder[PlaceDating] = WireEnum.decoder
  given Schema[PlaceDating] = WireEnum.schema

  /** A place's period in words, as a card says it: `1300 BCE–1100 BCE`, `from 1200 CE`, `until 1453 CE`,
    * `end 1769 CE, start unknown`, `date unknown`; empty for a stated timeless place.
    */
  def describe(dating: PlaceDating, start: Option[Long], end: Option[Long]): String = dating match
    case Unknown => "date unknown"
    case EndOnly => end.fold("date unknown")(e => s"end ${YearFmt(e)}, start unknown")
    case Stated  => YearFmt.openPeriod(start, end, none = "")
