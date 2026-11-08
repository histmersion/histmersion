package com.histmersion
package util.database

/** A user's text as a `LIKE`/`ILIKE` pattern with `\` as the escape character, so a `%` or `_` in it is literal. Inside
  * a Magnum `sql"…"` the clause is spelt `ESCAPE '\\'`: the interpolator processes escapes like `s"…"`.
  */
object LikePattern:
  type ContainsS = String
  type StartsWithS = String

  def escape(s: String): String = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
  def contains(s: ContainsS): String = "%" + escape(s) + "%"
  def startsWith(s: StartsWithS): String = escape(s) + "%"
