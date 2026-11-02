package com.histmersion.domain.research.articles

import com.histmersion.domain.research.ResearchData
import com.histmersion.api.Api
import com.histmersion.util.data.mirror.Mirror

/** An article's note in the Markdown mirror, with its generated coverage sections, as the articles' controllers render
  * it.
  */
final class ArticleMirrorNote(articles: ArticlesControllers) extends Mirror.Note:
  val records: Mirror.Records = ResearchData.ArticleNotes
  def note(id: String): Either[Api.Error, String] = articles.articleNote(id)
