package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class AssetLinkDeleted(
    id: Long,
    deleted: Boolean
) derives KindlingsCodecAsObject,
      KindlingsSchema

object AssetLinkDeleted:
  given Schema[AssetLinkDeleted] = summon[KindlingsSchema[AssetLinkDeleted]].schema
