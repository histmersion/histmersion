package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `pg_restore` exits 0 with "errors ignored on restore" when the dump came from a newer `pg_dump` than the server, so
  * the lines it complained about are reported.
  */
final case class RestoreResult(
    file: String,
    schemaVersion: String,
    sourcesBefore: Int,
    sourcesAfter: Int,
    durationMs: Long,
    warnings: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object RestoreResult:
  given Schema[RestoreResult] = summon[KindlingsSchema[RestoreResult]].schema
