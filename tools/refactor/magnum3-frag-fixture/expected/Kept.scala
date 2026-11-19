package fixture

import com.augustnagro.magnum.{sql, Frag, SqlLiteral}
import fixture.util.PlaceSql

object Kept:
  private val Legacy: String = "legacy_column"

  // a name not configured keeps its SqlLiteral, so the import stays
  def both: Frag = sql"SELECT ${SqlLiteral(Legacy)} WHERE ${PlaceSql.importedSql(sql"q")}"

  // a triple-quoted literal with a backslash is kept (sql"""...""" would read it as an escape) and reported
  def regex: Frag = PlaceSql.pattern("""\d+""")

  // a call spliced into text (an s"...", a `+`) is kept: a Frag there would put its toString into the SQL
  def spliced: String = s"SELECT ${PlaceSql.label("p")} FROM places p"
  def joined: String = "WHERE " + PlaceSql.label("q")
