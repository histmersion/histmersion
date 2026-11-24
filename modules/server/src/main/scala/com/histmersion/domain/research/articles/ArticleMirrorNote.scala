package com.histmersion
package domain.research.articles

import com.histmersion.domain.research.ResearchData
import com.histmersion.api.Api
import com.histmersion.util.data.mirror.Mirror
import com.histmersion.util.data.mirror.MirrorPlacement
import com.histmersion.util.data.mirror.Frontmatter
import com.histmersion.domain.research.Slug
import com.histmersion.api.Write
import com.histmersion.lint.{LintRule, tolerated}

/** An article's note in the Markdown mirror, with its generated coverage sections, as the articles' controllers render
  * it.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class ArticleMirrorNote(articles: ArticlesControllers) extends Mirror.Note:
  val records: Mirror.Records = ResearchData.ArticleNotes
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def note(id: MirrorPlacement.RecordId): Either[Api.Error, Frontmatter.NoteBody] =
    Slug
      .parse(id.value)
      .left
      .map(p => Write.invalid(p))
      .flatMap(slug => articles.articleNote(slug).map(t => Frontmatter.NoteBody(t.value)))
