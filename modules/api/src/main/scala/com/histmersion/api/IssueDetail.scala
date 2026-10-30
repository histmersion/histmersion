package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class IssueDetail(
    fields: List[IssueField] = Nil,
    text: Option[IssueText] = None,
    place: Option[IssuePlace] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object IssueDetail:
  given Schema[IssueDetail] = summon[KindlingsSchema[IssueDetail]].schema
