package com.histmersion.domain.research.articles

import com.histmersion.domain.research.ResearchData
import com.histmersion.util.data.mirror.{MirrorNote, MirrorRecords}
import com.histmersion.api.Api

/** An article's note in the Markdown mirror, with its generated coverage sections, as the articles' controllers render
  * it.
  */
final class ArticleMirrorNote(articles: ArticlesControllers) extends MirrorNote:
  val records: MirrorRecords = ResearchData.ArticleNotes
  def note(id: String): Either[Api.Error, String] = articles.articleNote(id)
