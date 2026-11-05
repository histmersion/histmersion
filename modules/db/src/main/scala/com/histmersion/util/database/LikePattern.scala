package com.histmersion.util.database

/** A user's text as a `LIKE`/`ILIKE` pattern with `\` as the escape character, so a `%` or `_` in it is literal. Inside
  * a Magnum `sql"…"` the clause is spelt `ESCAPE '\\'`: the interpolator processes escapes like `s"…"`.
  */
object LikePattern:
  type EscapeResult = EscapeResult.T
  object EscapeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscapeS = EscapeS.T
  object EscapeS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContainsResult = ContainsResult.T
  object ContainsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContainsS = ContainsS.T
  object ContainsS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartsWithResult = StartsWithResult.T
  object StartsWithResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartsWithS = StartsWithS.T
  object StartsWithS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def escape(s: com.histmersion.util.database.LikePattern.EscapeS): com.histmersion.util.database.LikePattern.EscapeResult = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
  def contains(s: com.histmersion.util.database.LikePattern.ContainsS): com.histmersion.util.database.LikePattern.ContainsResult = "%" + escape(s) + "%"
  def startsWith(s: com.histmersion.util.database.LikePattern.StartsWithS): com.histmersion.util.database.LikePattern.StartsWithResult = escape(s) + "%"
