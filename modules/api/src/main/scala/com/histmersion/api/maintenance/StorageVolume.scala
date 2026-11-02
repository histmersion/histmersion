package com.histmersion.api.maintenance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class StorageVolume(id: String, totalBytes: Long, freeBytes: Long)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object StorageVolume:
  given Schema[StorageVolume] = summon[KindlingsSchema[StorageVolume]].schema
