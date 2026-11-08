package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.{Codec, Decoder, Encoder}
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.domain.library.Citekey
import com.histmersion.api.library.Source
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.domain.library.sources.SourcesControllers

/** `get_source` as an agent calls it: no `fullText` (`section: "body"` says the same), and a body slice (`offset`,
  * `chars`, `heading`) without a section reads the body — the MCP tool would answer the summary and ignore the slice.
  */
final case class AgentGetSourceInput(
    citekey: Citekey,
    section: Option[Source.Section.Wire] = None,
    heading: Option[SourceService.Heading] = None,
    offset: Option[Offset] = None,
    chars: Option[SourcesControllers.Chars] = None
) derives KindlingsCodecAsObject:
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
  private given Codec[Citekey] =
    Codec.from(
      Decoder.decodeString.map(Citekey.assume(_)),
      Encoder.encodeString.contramap(_.value)
    )

  given Schema[AgentGetSourceInput] = KindlingsSchema
    .derived[AgentGetSourceInput]
    .schema
    .modify(_.section)(_.description("summary|findings|notes|toc|body|all (default: summary+findings+notes)"))
    .modify(_.heading)(_.description("Only the body under this heading (prefix match)"))
    .modify(_.offset)(_.description("Body slice start (a character offset, e.g. from passages)"))
    .modify(_.chars)(_.description("Body slice length"))
