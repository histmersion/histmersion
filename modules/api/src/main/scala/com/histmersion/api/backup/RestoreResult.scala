package com.histmersion
package api.backup
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `pg_restore` exits 0 with "errors ignored on restore" when the dump came from a newer `pg_dump` than the server, so
  * the lines it complained about are reported.
  */
final case class RestoreResult(
    file: RestoreResult.File,
    schemaVersion: RestoreResult.SchemaVersion,
    sourcesBefore: RestoreResult.SourcesBefore,
    sourcesAfter: RestoreResult.SourcesAfter,
    durationMs: RestoreResult.DurationMs,
    warnings: List[RestoreResult.Warnings] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object RestoreResult:
  export Backup.Result.File
  type SchemaVersion = SchemaVersion.T
  object SchemaVersion extends RefinedType[String, Pure]
  type SourcesBefore = SourcesBefore.T
  object SourcesBefore extends RefinedType[Int, Pure]
  type SourcesAfter = SourcesAfter.T
  object SourcesAfter extends RefinedType[Int, Pure]
  export Backup.Result.DurationMs
  type Warnings = Warnings.T
  object Warnings extends RefinedType[String, Pure]
