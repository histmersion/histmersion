package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class FollowupRow(
    title: Option[String] = None,
    year: Option[Int] = None,
    doi: Option[String] = None,
    arxiv: Option[String] = None,
    citedBy: Option[Int] = None,
    isInfluential: Boolean = false,
    intents: List[String] = Nil,
    contexts: List[String] = Nil,
    proposedKind: String = "cites",
    citekey: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object FollowupRow:
  given Schema[FollowupRow] = summon[KindlingsSchema[FollowupRow]].schema
