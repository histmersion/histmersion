package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class Backlink(kind: String, id: String, title: Option[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object Backlink:
  given Schema[Backlink] = summon[KindlingsSchema[Backlink]].schema
