package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `GET /api/articles/{slug}` — the article with both directions of its link graph resolved, and the formatting
  * problems of its body and summary ([[FormatIssue]]: what will not display as the writer meant), so an agent sees them
  * right after reading or writing it.
  */
final case class ArticleView(
    article: ArticleRow,
    backlinks: List[Backlink],
    sourcesCited: List[CitedSource],
    formatting: List[FormatIssue] = Nil,
    /** The coverage of its recorded filter (#958) — `None` when it records none. */
    coverage: Option[Coverage] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ArticleView:
  given Schema[ArticleView] = summon[KindlingsSchema[ArticleView]].schema
