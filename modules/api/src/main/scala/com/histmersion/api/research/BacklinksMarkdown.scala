package com.histmersion.api.research

object BacklinksMarkdown:


  def sourceArticles(rows: Vector[Backlink]): com.histmersion.api.Markdown.Text =
    if rows.isEmpty then _root_.com.histmersion.api.Markdown.Text("")
    else
      val items = rows.map(b => s"- [${b.title.getOrElse(b.id)}](/article/${b.id})")
      _root_.com.histmersion.api.Markdown.Text(s"\n\n## Mentioned in articles (${rows.size})\n${items.mkString("\n")}")
