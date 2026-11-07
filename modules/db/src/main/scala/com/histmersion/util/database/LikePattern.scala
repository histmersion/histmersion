package com.histmersion.util.database

/** A user's text as a `LIKE`/`ILIKE` pattern with `\` as the escape character, so a `%` or `_` in it is literal. Inside
  * a Magnum `sql"…"` the clause is spelt `ESCAPE '\\'`: the interpolator processes escapes like `s"…"`.
  */
object LikePattern:
  type ContainsS = String
  val ContainsS: String.type = String
  type StartsWithS = String
  val StartsWithS: String.type = String

  def escape(s: String): String = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
  def contains(s: com.histmersion.util.database.LikePattern.ContainsS): String = "%" + escape(s) + "%"
  def startsWith(s: com.histmersion.util.database.LikePattern.StartsWithS): String = escape(s) + "%"
