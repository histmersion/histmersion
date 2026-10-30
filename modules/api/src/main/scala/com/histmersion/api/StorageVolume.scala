package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class StorageVolume(id: String, totalBytes: Long, freeBytes: Long)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object StorageVolume:
  given Schema[StorageVolume] = summon[KindlingsSchema[StorageVolume]].schema
