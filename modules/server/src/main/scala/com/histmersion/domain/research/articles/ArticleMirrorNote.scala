package com.histmersion.domain.research.articles

import com.histmersion.api.ApiError
import com.histmersion.domain.research.ResearchData
import com.histmersion.util.data.mirror.{MirrorNote, MirrorRecords}

/** An article's note in the Markdown mirror, with its generated coverage sections, as the articles' controllers render
  * it.
  */
final class ArticleMirrorNote(articles: ArticlesControllers) extends MirrorNote:
  val records: MirrorRecords = ResearchData.ArticleNotes
  def note(id: String): Either[ApiError, String] = articles.articleNote(id)
