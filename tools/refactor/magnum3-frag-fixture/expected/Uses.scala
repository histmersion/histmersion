package fixture

import com.augustnagro.magnum.{sql, Frag}
import fixture.util.{ListSql, PlaceSql, RangeSql}
import fixture.util.Timestamps

object Uses:
  private def ts(col: String): String = col

  private val SummaryCols: Frag = sql"p.id, p.label"

  // a wrapped call: the literal becomes a fragment, the wrapper goes
  def range: Frag = sql"SELECT ${RangeSql.cols(sql"f.period")} FROM findings f"

  // a wrapped call renamed (this file's `ts` is the shared Timestamps.iso), its import added
  def created: Frag = sql"SELECT ${Timestamps.iso(sql"x.created_at")} FROM x"

  // names whose definitions are fragments now, bare and qualified
  def summary: Frag = sql"SELECT ${SummaryCols} FROM places p WHERE ${PlaceSql.Imported} IS NOT NULL"

  // a bare call: the literal at the SQL position, the other arguments as they are
  def kinds(vs: List[String]): Frag = sql"SELECT 1 FROM jobs WHERE true${ListSql.anyOf(sql"kind", vs)}"

  // an argument that is already a fragment stays
  def kindsOf(expr: Frag, vs: List[String]): Frag = ListSql.anyOf(expr, vs)

  // a dollar sign in the literal is escaped for the interpolator
  def dollar: Frag = sql"SELECT ${PlaceSql.importedSql(sql"p$$")}"

  // a non-SQL argument (a number) stays as it is
  def year: Frag = sql"WHERE ${PlaceSql.inTime(sql"p", 5L)}"
