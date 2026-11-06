package com.histmersion.api.research

object BacklinksMarkdown:

  type SourceArticlesResult = SourceArticlesResult.T
  object SourceArticlesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def sourceArticles(rows: Vector[Backlink]): com.histmersion.api.research.BacklinksMarkdown.SourceArticlesResult =
    if rows.isEmpty then _root_.com.histmersion.api.research.BacklinksMarkdown.SourceArticlesResult("")
    else
      val items = rows.map(b => s"- [${b.title.getOrElse(b.id)}](/article/${b.id})")
      _root_.com.histmersion.api.research.BacklinksMarkdown.SourceArticlesResult(s"\n\n## Mentioned in articles (${rows.size})\n${items.mkString("\n")}")
