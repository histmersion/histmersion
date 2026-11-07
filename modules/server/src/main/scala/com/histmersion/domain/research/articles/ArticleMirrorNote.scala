package com.histmersion.domain.research.articles

import com.histmersion.domain.research.ResearchData
import com.histmersion.api.Api
import com.histmersion.util.data.mirror.Mirror

/** An article's note in the Markdown mirror, with its generated coverage sections, as the articles' controllers render
  * it.
  */
final class ArticleMirrorNote(articles: ArticlesControllers) extends Mirror.Note:
  val records: Mirror.Records = ResearchData.ArticleNotes
  def note(id: com.histmersion.util.data.mirror.MirrorPlacement.RecordId): Either[Api.Error, com.histmersion.util.data.mirror.Frontmatter.NoteBody] =
    com.histmersion.domain.research.Slug
      .parse(id.value)
      .left
      .map(p => com.histmersion.api.Write.invalid(com.histmersion.api.Write.InvalidMessage(p.value)))
      .flatMap(slug => articles.articleNote(slug).map(t => com.histmersion.util.data.mirror.Frontmatter.NoteBody(t.value)))
