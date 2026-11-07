package com.histmersion.domain.research.articles

import com.histmersion.domain.research.ResearchData
import com.histmersion.api.Api
import com.histmersion.util.data.mirror.Mirror

/** An article's note in the Markdown mirror, with its generated coverage sections, as the articles' controllers render
  * it.
  */
final class ArticleMirrorNote(articles: ArticlesControllers) extends Mirror.Note:
  val records: Mirror.Records = ResearchData.ArticleNotes
  def note(id: com.histmersion.domain.research.articles.ArticleMirrorNote.NoteId): Either[Api.Error, com.histmersion.util.data.mirror.Frontmatter.NoteBody] = articles.articleNote(id)

object ArticleMirrorNote:
  type NoteId = NoteId.T
  object NoteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
