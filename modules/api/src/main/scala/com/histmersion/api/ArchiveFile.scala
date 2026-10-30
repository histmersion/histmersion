package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── archive.org ────────────────────────────────────────────────────────────────────────────────

final case class ArchiveFile(name: String, format: Option[String], megabytes: Double)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object ArchiveFile:
  given Schema[ArchiveFile] = summon[KindlingsSchema[ArchiveFile]].schema
