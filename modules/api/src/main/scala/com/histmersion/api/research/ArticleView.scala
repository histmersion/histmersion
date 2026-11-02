package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.checks.FormatIssue
import com.histmersion.api.given

final case class ArticleView(
    article: ArticleRow,
    backlinks: List[Backlink],
    sourcesCited: List[CitedSource],
    formatting: List[FormatIssue] = Nil,
    coverage: Option[Coverage] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ArticleView:
  given Schema[ArticleView] = summon[KindlingsSchema[ArticleView]].schema
