package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class DeleteBlocker(kind: DeleteBlockerKind, id: String, title: Option[String] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object DeleteBlocker:
  given Schema[DeleteBlocker] = summon[KindlingsSchema[DeleteBlocker]].schema
