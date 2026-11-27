package com.histmersion
package util.database

import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** A user's text as a `LIKE`/`ILIKE` pattern with `\` as the escape character, so a `%` or `_` in it is literal. Inside
  * a Magnum `sql"…"` the clause is spelt `ESCAPE '\\'`: the interpolator processes escapes like `s"…"`.
  */
@atBoundary(BoundaryKind.Sql, "escapes text into a LIKE pattern")
object LikePattern:
  type ContainsS = String
  type StartsWithS = String

  def escape(s: String): String = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def contains(s: ContainsS): String = "%" + escape(s) + "%"
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def startsWith(s: StartsWithS): String = escape(s) + "%"
