package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class ReadMark(
    kind: ItemKind,
    id: ItemKey,
    title: String,
    read: Boolean = false,
    readAt: Option[String] = None,
    openedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object ReadMark:
  given Schema[ReadMark] = summon[KindlingsSchema[ReadMark]].schema
