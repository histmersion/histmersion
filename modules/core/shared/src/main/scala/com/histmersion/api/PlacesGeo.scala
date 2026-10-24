package com.histmersion.api

import sttp.tapir.Schema

/** How the map's area layer reads a time RANGE (`GET /api/places/geo?from=&to=&pick=`): a snapshot at
  * one year of it — the middle (the default, what the map always did), the start or the end — or every
  * state alive at ANY time in it, one shape per state: its earliest or its latest version there.
  * "The same state over time" is the same kind and normalised label (`db.PlaceRepo.geoJsonQuery`). */
enum GeoPick:
  case Mid, Start, End, Earliest, Latest

  def wire: String = this match
    case Mid      => "mid"
    case Start    => "start"
    case End      => "end"
    case Earliest => "earliest"
    case Latest   => "latest"

  /** The map's menu option (the menu is "States shown"); each reads on its own, as a closed menu shows it. */
  def label: String = this match
    case Mid      => "States at midpoint"
    case Start    => "States at start"
    case End      => "States at end"
    case Earliest => "All states in period (earliest shape)"
    case Latest   => "All states in period (latest shape)"

  /** A snapshot at one year of the range, not the whole range. */
  def snapshot: Boolean = this match
    case Mid | Start | End => true
    case _                 => false

object GeoPick:
  def parse(s: String): Option[GeoPick] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  given WireEnumValues[GeoPick]:
    def all: Array[GeoPick] = GeoPick.values
    def wire(e: GeoPick): String = e.wire
    def fromWire(s: String): Option[GeoPick] = parse(s)
  given io.circe.Encoder[GeoPick] = WireEnum.encoder
  given io.circe.Decoder[GeoPick] = WireEnum.decoder
  given Schema[GeoPick]           = WireEnum.schema

  /** Pure: the year a snapshot pick reads the range `lo..hi` at (`lo ≤ hi`). The middle is the map's
    * own `(lo + hi) / 2` (truncated), so a snapshot shares its year — and its cached answer — with
    * the sites layer and playback. */
  def yearOf(pick: GeoPick, lo: Long, hi: Long): Long = pick match
    case Start => lo
    case End   => hi
    case _     => (lo + hi) / 2

  /** Pure: what `GET /api/places/geo` draws for its time parameters. `from`/`to` give the range (one
    * alone is a range of one year; reversed, they are put in order) and `pick` reads it; without them
    * `asOf` is the year, and without that nothing is time-sliced. A range of one year is a snapshot. */
  def resolve(asOf: Option[Long], from: Option[Long], to: Option[Long], pick: GeoPick): GeoTime =
    val span = (from, to) match
      case (Some(a), Some(b)) => Some((a.min(b), a.max(b)))
      case (Some(a), None)    => Some((a, a))
      case (None, Some(b))    => Some((b, b))
      case (None, None)       => None
    span match
      case None                                        => asOf.fold(GeoTime.Always)(GeoTime.At(_))
      case Some((lo, hi)) if pick.snapshot || lo == hi => GeoTime.At(yearOf(pick, lo, hi))
      case Some((lo, hi))                              => GeoTime.Within(lo, hi, latest = pick == Latest)

/** The time a map layer is drawn for ([[GeoPick.resolve]]). */
enum GeoTime:
  /** No time: every shape, successive versions all drawn. */
  case Always
  /** A snapshot: what was alive in `year`, each state once. */
  case At(year: Long)
  /** Every state alive at any time in `from..to` (inclusive), one shape each: its earliest version
    * there, or its latest when `latest`. */
  case Within(from: Long, to: Long, latest: Boolean)

  /** The inclusive window a time rule reads, if any. */
  def window: Option[(Long, Long)] = this match
    case Always            => None
    case At(y)             => Some((y, y))
    case Within(lo, hi, _) => Some((lo, hi))
