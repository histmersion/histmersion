package com.histmersion.api.backup
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

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
      KindlingsSchema, com.histmersion.api.json.Schema
