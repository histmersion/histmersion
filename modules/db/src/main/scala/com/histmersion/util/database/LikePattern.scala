package com.histmersion.util.database

/** A user's text as a `LIKE`/`ILIKE` pattern with `\` as the escape character, so a `%` or `_` in it is literal. Inside
  * a Magnum `sql"…"` the clause is spelt `ESCAPE '\\'`: the interpolator processes escapes like `s"…"`.
  */
object LikePattern:
  def escape(s: String): String = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
  def contains(s: String): String = "%" + escape(s) + "%"
  def startsWith(s: String): String = escape(s) + "%"
