package com.histmersion
package util.database

import com.augustnagro.magnum.{sql, Frag}

/** The ONE rendering of a `timestamptz` column as the wire's ISO-8601 text, in UTC with a `Z`: [[iso]] to the
  * microsecond (what a bundle, the personal lists and the activity carry, so two clocks compare as strings) and
  * [[isoSeconds]] to the second (the agents', goals' and managers' rows). Each takes the column (or any expression) as
  * a fragment, so nothing but SQL reaches the text.
  */
object Timestamps:

  /** `'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'` — the `to_char` format of [[iso]]. */
  val IsoFormat: Frag = sql"""'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'"""

  /** `'YYYY-MM-DD"T"HH24:MI:SS"Z"'` — the `to_char` format of [[isoSeconds]]. */
  val IsoSecondsFormat: Frag = sql"""'YYYY-MM-DD"T"HH24:MI:SS"Z"'"""

  /** `to_char(col AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')`: microseconds. */
  def iso(col: Frag): Frag = sql"to_char($col AT TIME ZONE 'UTC', $IsoFormat)"

  /** `to_char(col AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')`: seconds. */
  def isoSeconds(col: Frag): Frag = sql"to_char($col AT TIME ZONE 'UTC', $IsoSecondsFormat)"
