package com.histmersion.api.backup
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

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
  type File = com.histmersion.api.backup.Backup.Result.File
  val File: _root_.com.histmersion.api.backup.Backup.Result.File.type = _root_.com.histmersion.api.backup.Backup.Result.File
  type SchemaVersion = SchemaVersion.T
  object SchemaVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcesBefore = SourcesBefore.T
  object SourcesBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourcesAfter = SourcesAfter.T
  object SourcesAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DurationMs = com.histmersion.api.backup.Backup.Result.DurationMs
  val DurationMs: _root_.com.histmersion.api.backup.Backup.Result.DurationMs.type = _root_.com.histmersion.api.backup.Backup.Result.DurationMs
  type Warnings = Warnings.T
  object Warnings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
