package com.histmersion
package domain.research.articles

import com.histmersion.api.Problem
import com.histmersion.domain.research.ResearchData
import com.histmersion.api.Api
import com.histmersion.util.data.mirror.Mirror
import com.histmersion.util.data.mirror.MirrorPlacement
import com.histmersion.util.data.mirror.Frontmatter
import com.histmersion.domain.research.Slug
import com.histmersion.api.Write

/** An article's note in the Markdown mirror, with its generated coverage sections, as the articles' controllers render
  * it.
  */
final class ArticleMirrorNote(articles: ArticlesControllers) extends Mirror.Note:
  val records: Mirror.Records = ResearchData.ArticleNotes
  def note(id: MirrorPlacement.RecordId): Either[Api.Error, Frontmatter.NoteBody] =
    Slug
      .parse(id.value)
      .left
      .map(p => Write.invalid(Problem(p.value)))
      .flatMap(slug => articles.articleNote(slug).map(t => Frontmatter.NoteBody(t.value)))
