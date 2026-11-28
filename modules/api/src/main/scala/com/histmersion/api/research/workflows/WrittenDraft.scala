package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.SName
import sttp.tapir.Schema.annotations.customise
import sttp.tapir.Schema.annotations.description

@customise(_.name(SName("DraftAnswer")))
final case class WrittenDraft(
    @description("The article's title (a new article); repeat the current title when extending") title: WrittenDraft.Title,
    @description("One or two plain sentences: what the article answers") summary: Article.SummaryText,
    @description("New article: the WHOLE body in Markdown. Extending: ONLY the new text to add") body: Article.Body,
    @description("Extending: the existing heading to add the text under (e.g. '## Disagreements'); null = at the end") section: Option[Article.Section] = None,
    @description("For each source of the claim table you do NOT cite: one plain sentence saying why it is left out (code lists it under the generated Not used section)") reasons: List[Coverage.Reason] = Nil
) derives JsonCodec, KindlingsSchema, Schema

object WrittenDraft:
  type Title = Title.T
  object Title extends Newtype[String, Pure]
