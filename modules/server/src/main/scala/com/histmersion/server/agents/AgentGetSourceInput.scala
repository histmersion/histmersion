package com.histmersion.server.agents

import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `get_source` as an agent calls it: no `fullText` (`section: "body"` says the same), and a body slice (`offset`,
  * `chars`, `heading`) without a section reads the body — the MCP tool would answer the summary and ignore the slice.
  */
final case class AgentGetSourceInput(
    citekey: String,
    section: Option[String] = None,
    heading: Option[String] = None,
    offset: Option[Int] = None,
    chars: Option[Int] = None
) derives KindlingsCodecAsObject:
  def sectionOrBody: Option[String] =
    section
      .map(_.trim)
      .filter(_.nonEmpty)
      .orElse(Option.when(offset.isDefined || chars.isDefined || heading.isDefined)("body"))
object AgentGetSourceInput:
  given Schema[AgentGetSourceInput] = KindlingsSchema
    .derived[AgentGetSourceInput]
    .schema
    .modify(_.section)(_.description("summary|findings|notes|toc|body|all (default: summary+findings+notes)"))
    .modify(_.heading)(_.description("Only the body under this heading (prefix match)"))
    .modify(_.offset)(_.description("Body slice start (a character offset, e.g. from passages)"))
    .modify(_.chars)(_.description("Body slice length"))
