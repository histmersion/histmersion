package com.histmersion.api.research.articles

import com.histmersion.api.*

object BacklinksMarkdown:

  def sourceArticles(rows: Vector[Backlink]): String =
    if rows.isEmpty then ""
    else
      val items = rows.map(b => s"- [${b.title.getOrElse(b.id)}](/article/${b.id})")
      s"\n\n## Mentioned in articles (${rows.size})\n${items.mkString("\n")}"
