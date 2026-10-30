package com.histmersion.server.agents

import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `facets` as an agent calls it: one field's values, optionally by prefix. */
final case class AgentFacetsInput(field: String, prefix: Option[String] = None) derives KindlingsCodecAsObject
object AgentFacetsInput:
  given Schema[AgentFacetsInput] = KindlingsSchema
    .derived[AgentFacetsInput]
    .schema
    .modify(_.field)(_.description("tags|authors|places|topics|status"))
    .modify(_.prefix)(_.description("Only values starting with this"))
