package fixture
package util

import com.augustnagro.magnum.{sql, Frag}

/** The helpers as the hand edit leaves them: Frag in, Frag out (the rule only follows at the call sites). */
object RangeSql:
  def start(col: Frag): Frag = sql"lower($col)"
  def end(col: Frag): Frag = sql"upper($col) - 1"
  def cols(col: Frag): Frag = sql"${start(col)}, ${end(col)}"

object Timestamps:
  def iso(col: Frag): Frag = sql"""to_char($col AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')"""

object ListSql:
  def anyOf(expr: Frag, vs: List[String]): Frag =
    if vs.isEmpty then sql"" else sql" AND $expr = ANY(string_to_array(${vs.mkString(",")}, ','))"

object PlaceSql:
  val Imported: Frag = sql"('wikidata', 'geonames')"
  def importedSql(alias: Frag): Frag = sql"coalesce($alias.import_source, '') IN $Imported"
  def inTime(alias: Frag, year: Long): Frag = sql"$alias.period @> $year::bigint"
  /** Still taking text: the rule keeps a literal it cannot copy (a triple-quoted one with a backslash). */
  def pattern(regex: String): Frag = sql"label ~ $regex"
  /** Not yet a fragment: its calls spliced into text stay as they are. */
  def label(alias: String): String = s"$alias.label"
