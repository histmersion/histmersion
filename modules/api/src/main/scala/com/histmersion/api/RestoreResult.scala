package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The outcome of a restore: the schema version Flyway validated afterwards, how many sources the database held before
  * and after, and any line `pg_restore` complained about (it exits 0 with "errors ignored on restore" when a dump was
  * taken by a NEWER pg_dump than the server).
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
