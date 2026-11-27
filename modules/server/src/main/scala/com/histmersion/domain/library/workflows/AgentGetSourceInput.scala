package com.histmersion
package domain.library.workflows
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.Source
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.domain.library.sources.SourcesControllers
import com.histmersion.lint.{tolerated, LintRule}

/** `get_source` as an agent calls it: no `fullText` (`section: "body"` says the same), and a body slice (`offset`,
  * `chars`, `heading`) without a section reads the body — the MCP tool would answer the summary and ignore the slice.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class AgentGetSourceInput(
    citekey: Citekey,
    section: Option[Source.Section.Wire] = None,
    heading: Option[SourceService.Heading] = None,
    offset: Option[Offset] = None,
    chars: Option[SourcesControllers.Chars] = None
) derives JsonCodec:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sectionOrBody: Option[Source.Section.Wire] =
    section
      .map(s => Source.Section.Wire(s.value.trim))
      .filter(_.value.nonEmpty)
      .orElse(
        Option.when(offset.isDefined || chars.isDefined || heading.isDefined)(
          Source.Section.Wire(Source.Section.Body)
        )
      )
object AgentGetSourceInput:

  /** What a model typed as the citekey is looked up as it stands: a key that names no source answers "not found", not a
    * refused turn.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private given JsonValueCodec[Citekey] = JsonCodec.text[Citekey](_.value, s => Right(Citekey.assume(s)))

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[AgentGetSourceInput] = KindlingsSchema
    .derived[AgentGetSourceInput]
    .schema
    .modify(_.section)(_.description("summary|findings|notes|toc|body|all (default: summary+findings+notes)"))
    .modify(_.heading)(_.description("Only the body under this heading (prefix match)"))
    .modify(_.offset)(_.description("Body slice start (a character offset, e.g. from passages)"))
    .modify(_.chars)(_.description("Body slice length"))
