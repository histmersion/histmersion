package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.ItemKind

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

  final case class Set(kind: ItemKind, id: ItemKey, read: Boolean, title: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema

  object Set:
    given Schema[Set] = summon[KindlingsSchema[Set]].schema
