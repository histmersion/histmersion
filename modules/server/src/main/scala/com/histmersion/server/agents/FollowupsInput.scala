package com.histmersion.server.agents

import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `followups` as an agent may call it: no `apply` — a model never writes edges. */
final case class FollowupsInput(id: String, since: Option[Int] = None, limit: Option[Int] = None)
    derives KindlingsCodecAsObject
object FollowupsInput:
  given Schema[FollowupsInput] = KindlingsSchema
    .derived[FollowupsInput]
    .schema
    .modify(_.id)(_.description("A citekey, DOI or arxiv:<id>"))
    .modify(_.since)(_.description("Only papers published from this year on"))
    .modify(_.limit)(_.description("At most this many citing papers"))
