package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A file system the entries sit on, and its free space (what this process may still write). */
final case class StorageVolume(id: String, totalBytes: Long, freeBytes: Long)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object StorageVolume:
  given Schema[StorageVolume] = summon[KindlingsSchema[StorageVolume]].schema
