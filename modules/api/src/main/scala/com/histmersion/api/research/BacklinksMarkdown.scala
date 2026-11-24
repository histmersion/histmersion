package com.histmersion
package api.research

import com.histmersion.lint.{LintRule, tolerated}
object BacklinksMarkdown:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sourceArticles(rows: Vector[Backlink]): String =
    if rows.isEmpty then ("")
    else
      val items = rows.map(b => s"- [${b.title.getOrElse(b.id)}](/article/${b.id})")
      (s"\n\n## Mentioned in articles (${rows.size})\n${items.mkString("\n")}")
