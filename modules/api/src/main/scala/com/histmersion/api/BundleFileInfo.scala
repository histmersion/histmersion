package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One NDJSON file: its path, how many lines, its size and its sha256. */
final case class BundleFileInfo(path: String, lines: Int, bytes: Long, sha256: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object BundleFileInfo:
  given Schema[BundleFileInfo] = summon[KindlingsSchema[BundleFileInfo]].schema
