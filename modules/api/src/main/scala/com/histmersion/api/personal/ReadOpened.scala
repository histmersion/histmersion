package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class ReadOpened(kind: ItemKind, id: ItemKey, title: Option[String] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema

object ReadOpened:
  given Schema[ReadOpened] = summon[KindlingsSchema[ReadOpened]].schema
