package com.histmersion.api

/** The time a map layer is drawn for ([[GeoPick.resolve]]). */
enum GeoTime:
  /** No time: every shape, successive versions all drawn. */
  case Always

  /** A snapshot: what was alive in `year`, each state once. */
  case At(year: Long)

  /** Every state alive at any time in `from..to` (inclusive), one shape each: its earliest version there, or its latest
    * when `latest`.
    */
  case Within(from: Long, to: Long, latest: Boolean)

  /** The inclusive window a time rule reads, if any. */
  def window: Option[(Long, Long)] = this match
    case Always            => None
    case At(y)             => Some((y, y))
    case Within(lo, hi, _) => Some((lo, hi))
